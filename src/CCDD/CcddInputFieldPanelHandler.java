/**
 * CFS Command & Data Dictionary description and data field panel handler.
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.text.JTextComponent;

import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.WrapLayout;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddUndoHandler.UndoableCheckBox;
import CCDD.CcddUndoHandler.UndoableDataFieldPanel;
import CCDD.CcddUndoHandler.UndoableTextArea;
import CCDD.CcddUndoHandler.UndoableTextField;

/******************************************************************************
 * CFS Command & Data Dictionary description and data field panel handler
 * class. The editor panel contains the table description and data fields (if
 * any)
 *****************************************************************************/
public abstract class CcddInputFieldPanelHandler
{
    // Class references
    private CcddFieldEditorDialog dataFieldEditorDlg;
    private CcddUndoManager undoManager;
    private CcddUndoHandler undoHandler;
    private CcddFieldHandler dataFieldHandler;
    private UndoableDataFieldPanel undoFieldPnl;

    // Components referenced by multiple methods
    private UndoableTextArea descriptionFld;
    private JPanel inputPnl;
    private Border border;
    private JPanel fieldPnl;
    private GridBagConstraints gbc;

    // Name of the owner (table or group) for the field panel handler
    private String ownerName;

    // Reference to the dialog that created this field panel handler
    private Component fieldPnlHndlrOwner;

    // Description field scroll pane (not used with table descriptions)
    private JScrollPane descScrollPane;

    // Width of the widest data field, including its label
    private int maxFieldWidth;

    /**************************************************************************
     * Get a reference to the description and data field panel handler
     *
     * @return Reference to the description and data field panel handler
     *************************************************************************/
    protected CcddInputFieldPanelHandler getInputFieldPanelHandler()
    {
        return this;
    }

    /**************************************************************************
     * Get the JPanel containing the description and data fields
     *
     * @return JPanel containing the description and data fields
     *************************************************************************/
    protected JPanel getFieldPanel()
    {
        return inputPnl;
    }

    /**************************************************************************
     * Get the description and data field undo/redo manager
     *
     * @return Reference to the description and data field undo/redo manager
     *************************************************************************/
    protected CcddUndoManager getFieldPanelUndoManager()
    {
        return undoManager;
    }

    /**************************************************************************
     * Set the references to the description and data field undo/redo manager
     * and handler
     *
     * @param undoManager
     *            undo/redo manager
     *
     * @param undoHandler
     *            undoable component handler
     *************************************************************************/
    protected void setEditPanelUndo(CcddUndoManager undoManager,
                                    CcddUndoHandler undoHandler)
    {
        this.undoManager = undoManager;
        this.undoHandler = undoHandler;
    }

    /**************************************************************************
     * Get a reference to the owner of the description and data field panel
     * handler
     *
     * @return Reference to the owner of the description and data field panel
     *         handler
     *************************************************************************/
    protected Component getOwner()
    {
        return fieldPnlHndlrOwner;
    }

    /**************************************************************************
     * Get the name of the owner of this description and data field panel
     * handler
     *
     * @return Name of the owner of this description and data field panel
     *         handler
     *************************************************************************/
    protected String getOwnerName()
    {
        return ownerName;
    }

    /**************************************************************************
     * Set the name of the owner of this description and data field panel
     * handler
     *
     * @param owner
     *            name of the owner of this description and data field panel
     *            handler
     *************************************************************************/
    protected void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    /**************************************************************************
     * Get the data field information for this field panel handler
     *
     * @return Data field information for this field panel handler
     *************************************************************************/
    protected CcddFieldHandler getDataFieldHandler()
    {
        return dataFieldHandler;
    }

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
        Color backColor = enable
                                ? ModifiableColorInfo.INPUT_BACK.getColor()
                                : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor();
        descriptionFld.setText(description);
        descriptionFld.setEditable(enable);
        descriptionFld.setBackground(backColor);
        descScrollPane.setBackground(backColor);
    }

    /**************************************************************************
     * Update the description field text. This puts the edit on the undo/redo
     * stack
     *************************************************************************/
    protected void updateDescriptionField()
    {
        descriptionFld.updateText();
    }

    /**************************************************************************
     * Get the width of the widest data field (including its label), plus
     * padding on either side
     *
     * @return Width of the widest data field (including its label), plus
     *         padding on either side
     *************************************************************************/
    protected int getMaxFieldWidth()
    {
        return maxFieldWidth + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
    }

    /**************************************************************************
     * Set the data field information for use when creating the data field
     * panel
     *
     * @param fieldInfo
     *            list of the data field information
     *************************************************************************/
    protected void setDataFieldInformation(List<FieldInformation> fieldInfo)
    {
        dataFieldHandler.setFieldInformation(fieldInfo);
    }

    /**************************************************************************
     * Store the data field information in the undo handler in case an
     * undo/redo operation is requested so that the fields can be set
     * accordingly
     *************************************************************************/
    protected void storeCurrentFieldInformation()
    {
        undoFieldPnl.setCurrentFieldInfo(dataFieldHandler.getFieldInformationCopy());
    }

    /**************************************************************************
     * Update the field information to match the data field text field and
     * check box values
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
                    fieldInfo.setValue(((JTextComponent) fieldInfo.getInputFld()).getText());
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
        return dataFieldEditorDlg;
    }

    /**************************************************************************
     * Set the currently active data field editor dialog
     *
     * @param fieldEditor
     *            currently active data field editor dialog
     *************************************************************************/
    protected void setFieldEditorDialog(CcddFieldEditorDialog fieldEditorDialog)
    {
        this.dataFieldEditorDlg = fieldEditorDialog;
    }

    /**************************************************************************
     * Create the table input field panel
     *
     * @param fieldPnlHndlrOwner
     *            reference to the owner of this description and data field
     *            handler
     *
     * @param scrollPane
     *            scroll pane containing the table; null if this field panel
     *            handler does not contain a table
     *
     * @param ownerName
     *            name of the owner of this field panel handler; null if no
     *            owner name is associated with it
     *
     * @param description
     *            description field text
     *
     * @param fieldHandler
     *            field handler reference
     *************************************************************************/
    protected void createDescAndDataFieldPanel(Component fieldPnlHndlrOwner,
                                               JScrollPane scrollPane,
                                               String ownerName,
                                               String description,
                                               CcddFieldHandler fieldHandler)
    {
        this.fieldPnlHndlrOwner = fieldPnlHndlrOwner;
        this.ownerName = ownerName;
        this.dataFieldHandler = fieldHandler;

        // Create the handler for undoing/redoing data field changes
        undoFieldPnl = undoHandler.new UndoableDataFieldPanel();

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
        inputPnl = new JPanel(new GridBagLayout());

        // Check if this editor contains a table
        if (scrollPane != null)
        {
            // Define the editor panel to contain the table
            JPanel innerPanel = new JPanel();
            innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
            innerPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            innerPanel.add(scrollPane);
            inputPnl.add(innerPanel, gbc);
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
        descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        descriptionLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());

        // Check if this editor doesn't contain a table
        if (scrollPane == null)
        {
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        }

        // Add the table description label
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
        gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridy++;
        descriptionPnl.add(descriptionLbl, gbc);

        // Create the description input field
        descriptionFld = undoHandler.new UndoableTextArea(description, 3, 20);
        descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        descriptionFld.setEditable(true);
        descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
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
            inputPnl.setBorder(BorderFactory.createEtchedBorder());
            gbc.gridy++;
            descriptionFld.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
            descriptionFld.setBorder(BorderFactory.createEmptyBorder());
            descScrollPane = new JScrollPane(descriptionFld);
            descScrollPane.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
            descScrollPane.setBorder(border);
            descScrollPane.setMinimumSize(descScrollPane.getPreferredSize());
            descriptionPnl.add(descScrollPane, gbc);
        }
        // The editor contains a table
        else
        {
            // Place the description field within a scroll pane and add the
            // field to the editor
            inputPnl.setBorder(BorderFactory.createEmptyBorder());
            gbc.gridx++;
            descriptionFld.setToolTipText(CcddUtilities.wrapText("Table description",
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            descriptionFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            descriptionFld.setBorder(border);
            descriptionPnl.add(descriptionFld, gbc);
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        }

        // Add the description panel to the editor
        gbc.weighty = 0.0;
        gbc.gridx = 0;
        gbc.gridy++;
        inputPnl.add(descriptionPnl, gbc);

        // Add the data field panel to the editor
        gbc.gridy++;
        gbc.insets.top = 0;
        gbc.insets.bottom = 0;
        createDataFieldPanel(false);

        // Add a listener for changes in the editor panel's size
        inputPnl.addComponentListener(new ComponentAdapter()
        {
            /******************************************************************
             * Handle resizing of the editor panel
             *****************************************************************/
            @Override
            public void componentResized(ComponentEvent ce)
            {
                // Create a runnable object to be executed
                SwingUtilities.invokeLater(new Runnable()
                {
                    /**********************************************************
                     * Since the size returned by get___Size() can lag the
                     * actual size, use invokeLater to let the sizes "catch up"
                     *********************************************************/
                    @Override
                    public void run()
                    {
                        // Revalidate to force the editor panel to redraw to
                        // the new sizes, which causes the data fields to be
                        // correctly sized so that all of the fields are
                        // visible
                        inputPnl.revalidate();
                    }
                });
            }
        });
    }

    /**************************************************************************
     * Create the data fields for display in the description and data field
     * panel
     *
     * @param undoable
     *            true if the change(s) to the data fields should be stored for
     *            possible undo/redo operations; false to not store the changes
     *************************************************************************/
    protected void createDataFieldPanel(boolean undoable)
    {
        maxFieldWidth = 0;

        // Set the preferred size so that the layout manager uses its default
        // sizing
        fieldPnlHndlrOwner.setPreferredSize(null);

        // Check if the data fields are already displayed
        if (fieldPnl != null)
        {
            // Remove the existing data fields
            inputPnl.remove(fieldPnl);
        }

        // Check if any data fields exist
        if (dataFieldHandler.getFieldInformation() != null
            && !dataFieldHandler.getFieldInformation().isEmpty())
        {
            // Create a panel to contain the data fields. As the editor is
            // resized the field panel is resized to contain the data fields,
            // wrapping them to new lines as needed
            fieldPnl = new JPanel(new WrapLayout(WrapLayout.LEADING));

            // Adjust the border to align the first field with the description
            // label
            fieldPnl.setBorder(BorderFactory.createEmptyBorder(-ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                               -ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                               0,
                                                               0));

            // Step through each data field
            for (final FieldInformation fieldInfo : dataFieldHandler.getFieldInformation())
            {
                switch (fieldInfo.getInputType())
                {
                    case BREAK:
                        // Create a text field for the separator so it can be
                        // handled like other fields
                        fieldInfo.setInputFld(undoHandler.new UndoableTextField());

                        // Add a vertical separator to the field panel
                        fieldPnl.add(new JSeparator(JSeparator.VERTICAL));
                        break;

                    case SEPARATOR:
                        // Create a text field for the separator so it can be
                        // handled like other fields
                        fieldInfo.setInputFld(undoHandler.new UndoableTextField());

                        // Add a horizontal separator to the field panel
                        fieldPnl.add(new JSeparator());
                        break;

                    case BOOLEAN:
                        // Create the data field check box
                        fieldInfo.setInputFld(undoHandler.new UndoableCheckBox(fieldInfo.getFieldName(),
                                                                               Boolean.valueOf(fieldInfo.getValue())));

                        UndoableCheckBox booleanCb = (UndoableCheckBox) fieldInfo.getInputFld();
                        booleanCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                        booleanCb.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());

                        // Set the check box's name so that the undo handler
                        // can identify the check box, even if it's destroyed
                        // and recreated
                        booleanCb.setName(fieldInfo.getOwnerName()
                                          + ","
                                          + fieldInfo.getFieldName());

                        // Adjust the left and right padding around the check
                        // box so that it is spaced the same as a text field
                        // data field
                        booleanCb.setBorder(BorderFactory.createEmptyBorder(0,
                                                                            ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                            0,
                                                                            ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()));

                        // Check if a description exists for this field
                        if (!fieldInfo.getDescription().isEmpty())
                        {
                            // Set the description as the tool tip text for
                            // this check box
                            booleanCb.setToolTipText(CcddUtilities.wrapText(fieldInfo.getDescription(),
                                                                            ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                        }

                        // And the check box to the field panel
                        fieldPnl.add(booleanCb);

                        // Store this check box's width if it is the largest
                        // data field width
                        maxFieldWidth = Math.max(maxFieldWidth,
                                                 booleanCb.getPreferredSize().width);

                        break;

                    default:
                        final JTextComponent inputFld;

                        // Create a panel for a single label and text field
                        // pair. This is necessary so that the two will stay
                        // together if line wrapping occurs due to a window
                        // size change
                        JPanel singleFldPnl = new JPanel(new FlowLayout(FlowLayout.LEADING,
                                                                        ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                        ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 4));
                        singleFldPnl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

                        // Create the data field label
                        JLabel fieldLbl = new JLabel(fieldInfo.getFieldName());
                        fieldLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                        fieldLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                        singleFldPnl.add(fieldLbl);

                        // Check if the input type is for multi-line text
                        if (fieldInfo.getInputType().equals(InputDataType.TEXT_MULTI))
                        {
                            // Create the data field input field as a text
                            // area, which allows new line characters which
                            // cause the field to be displayed in multiple rows
                            fieldInfo.setInputFld(undoHandler.new UndoableTextArea(fieldInfo.getValue(),
                                                                                   1,
                                                                                   fieldInfo.getSize()));
                            inputFld = (UndoableTextArea) fieldInfo.getInputFld();
                        }
                        // The input type is one other than for multi-line text
                        else
                        {
                            // // Create the data field input field as a text
                            // field, which allows a single rows
                            fieldInfo.setInputFld(undoHandler.new UndoableTextField(fieldInfo.getValue(),
                                                                                    fieldInfo.getSize()));
                            inputFld = (UndoableTextField) fieldInfo.getInputFld();
                        }

                        inputFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                        inputFld.setEditable(true);
                        inputFld.setBorder(border);
                        inputFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                        inputFld.setBackground(fieldInfo.getValue().isEmpty()
                                               && fieldInfo.isRequired()
                                                                        ? ModifiableColorInfo.REQUIRED_BACK.getColor()
                                                                        : ModifiableColorInfo.INPUT_BACK.getColor());

                        // Set the text field's name so that the undo handler
                        // can identify the text field, even if it's destroyed
                        // and recreated
                        inputFld.setName(fieldInfo.getOwnerName()
                                         + ","
                                         + fieldInfo.getFieldName());

                        // Check if a description exists for this field
                        if (!fieldInfo.getDescription().isEmpty())
                        {
                            // Set the description as the tool tip text for
                            // this text field
                            inputFld.setToolTipText(CcddUtilities.wrapText(fieldInfo.getDescription(),
                                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                        }

                        // Add the data field to the single field panel
                        singleFldPnl.add(inputFld);

                        // And the single field to the field panel
                        fieldPnl.add(singleFldPnl);

                        // Store this field's width if it is the largest data
                        // field width
                        maxFieldWidth = Math.max(maxFieldWidth,
                                                 singleFldPnl.getPreferredSize().width);

                        // Create an input field verifier for the data field
                        inputFld.setInputVerifier(new InputVerifier()
                        {
                            // Storage for the last valid value entered; used
                            // to restore the data field value if an invalid
                            // value is entered. Initialize to the value at the
                            // time the field is created
                            String lastValid = inputFld.getText();

                            /**************************************************
                             * Verify the contents of a the data field
                             *************************************************/
                            @Override
                            public boolean verify(JComponent input)
                            {
                                boolean isValid = true;

                                // Get the data field reference to shorten
                                // subsequent calls
                                JTextComponent inFld = (JTextComponent) input;

                                // Get the data field contents
                                String inputTxt = inFld.getText();

                                // Check if the field's input type doesn't
                                // allow leading and trailing white space
                                // characters
                                if (fieldInfo.getInputType() != InputDataType.TEXT_WHT_SPC
                                    && fieldInfo.getInputType() != InputDataType.TEXT_MULTI_WHT_SPC)
                                {
                                    // Remove leading and trailing white space
                                    // characters
                                    inputTxt = inputTxt.trim();
                                }

                                // Check if the field contains an illegal
                                // character
                                if (!fieldInfo.getInputType().getInputMatch().isEmpty()
                                    && !inputTxt.isEmpty()
                                    && !inputTxt.matches(fieldInfo.getInputType().getInputMatch()))
                                {
                                    // Inform the user that the data field
                                    // contents is invalid
                                    new CcddDialogHandler().showMessageDialog(fieldPnlHndlrOwner,
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
                                    if (fieldPnlHndlrOwner instanceof CcddFrameHandler)
                                    {
                                        ((CcddFrameHandler) fieldPnlHndlrOwner).setControlsEnabled(false);
                                        ((CcddFrameHandler) fieldPnlHndlrOwner).setControlsEnabled(true);
                                    }
                                    else if (fieldPnlHndlrOwner instanceof CcddDialogHandler)
                                    {
                                        ((CcddDialogHandler) fieldPnlHndlrOwner).setControlsEnabled(false);
                                        ((CcddDialogHandler) fieldPnlHndlrOwner).setControlsEnabled(true);
                                    }

                                    // Check if the data field is a text field
                                    if (input instanceof UndoableTextField)
                                    {
                                        // Restore the previous value in the
                                        // data field
                                        ((UndoableTextField) inFld).setText(lastValid, false);
                                    }
                                    // Check if the data field is a text area
                                    // (multi-line)
                                    else if (input instanceof UndoableTextArea)
                                    {
                                        // Restore the previous value in the
                                        // data field
                                        ((UndoableTextArea) inFld).setText(lastValid);
                                    }

                                    // Set the flag to indicate an invalid
                                    // value was entered
                                    isValid = false;
                                }
                                // The input is valid
                                else
                                {
                                    // Store the 'cleaned' text back into the
                                    // text field. For numeric types, reformat
                                    // the input value
                                    inFld.setText(fieldInfo.getInputType().formatInput(inputTxt));
                                    fieldInfo.setValue(inFld.getText());

                                    // Store the new value as the last valid
                                    // value
                                    lastValid = inFld.getText();

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
                inputPnl.add(fieldPnl, gbc);
            }
        }

        // Check if the data field panel change should be put in the undo/redo
        // stack
        if (undoable)
        {
            // Store the field information in the undo handler in case the
            // update needs to be undone
            undoFieldPnl.addDataFieldEdit(this,
                                          dataFieldHandler.getFieldInformationCopy());
        }

        // Force the owner of the editor panel to redraw so that changes to the
        // fields are displayed and the owner's size is adjusted
        fieldPnlHndlrOwner.revalidate();
        fieldPnlHndlrOwner.repaint();
        fieldPnlHndlrOwner.setMinimumSize(fieldPnlHndlrOwner.getPreferredSize());
    }

    /**************************************************************************
     * Clear the values from all fields
     *************************************************************************/
    protected void clearFieldValues()
    {
        // Disable automatically ending the edit sequence. This allows all of
        // the cleared fields to be grouped into a single sequence so that if
        // undone, all fields are restored
        undoHandler.setAutoEndEditSequence(false);

        for (FieldInformation fieldInfo : dataFieldHandler.getFieldInformation())
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
                // Get the reference to the data field
                JTextComponent inputFld = (JTextComponent) fieldInfo.getInputFld();

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
        undoHandler.setAutoEndEditSequence(true);
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
                // Check if any fields exist
                if (dataFieldHandler != null
                    && dataFieldHandler.getFieldInformation() != null)
                {
                    // Step through each field
                    for (FieldInformation fieldInfo : dataFieldHandler.getFieldInformation())
                    {
                        // Check if this isn't a boolean input (check box) data
                        // field
                        if (fieldInfo.getInputType() != InputDataType.BOOLEAN)
                        {
                            // Set the text field background color. If the
                            // field is empty and is flagged as required then
                            // set the background to indicate a value should be
                            // supplied
                            setFieldBackground(fieldInfo);
                        }
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
        ((JTextComponent) fieldInfo.getInputFld()).setBackground(fieldInfo.getValue().isEmpty()
                                                                 && fieldInfo.isRequired()
                                                                                          ? ModifiableColorInfo.REQUIRED_BACK.getColor()
                                                                                          : ModifiableColorInfo.INPUT_BACK.getColor());
    }
}
