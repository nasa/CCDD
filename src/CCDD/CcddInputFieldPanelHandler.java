/**
 * CFS Command and Data Dictionary description and data field panel handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National Aeronautics and Space
 * Administration. No copyright is claimed in the United States under Title 17, U.S. Code. All
 * Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
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
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.text.JTextComponent;

import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesComponent.WrapLayout;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddUndoHandler.UndoableCheckBox;
import CCDD.CcddUndoHandler.UndoableComboBox;
import CCDD.CcddUndoHandler.UndoableDataFieldPanel;
import CCDD.CcddUndoHandler.UndoableTextArea;
import CCDD.CcddUndoHandler.UndoableTextField;

/**************************************************************************************************
 * CFS Command and Data Dictionary description and data field panel handler class. The editor panel
 * contains the table description and data fields (if any)
 *************************************************************************************************/
public abstract class CcddInputFieldPanelHandler
{
    // Class references
    private CcddUndoManager undoManager;
    private CcddUndoHandler undoHandler;
    private CcddFieldHandler fieldHandler;
    private CcddInputTypeHandler inputTypeHandler;

    // Components referenced by multiple methods
    private JPanel inputPnl;
    private UndoableDataFieldPanel undoFieldPnl;
    private UndoableTextArea descriptionFld;
    private JPanel descAndFldPnl;
    private JPanel fieldPnl;
    private Border border;
    private GridBagConstraints gbc;

    // Name of the owner (table or group) for the field panel handler
    private String ownerName;

    // Reference to the dialog that created this field panel handler
    private Component fieldPnlHndlrOwner;

    // List of the information used to create the data fields in the input panel
    private List<FieldInformation> fieldInformation;

    // Description field scroll pane (not used with table descriptions)
    private JScrollPane descScrollPane;

    // Width of the widest data field, including its label
    private int maxFieldWidth;

    /**********************************************************************************************
     * Get a reference to the description and data field panel handler
     *
     * @return Reference to the description and data field panel handler
     *********************************************************************************************/
    protected CcddInputFieldPanelHandler getInputFieldPanelHandler()
    {
        return this;
    }

    /**********************************************************************************************
     * Get the JPanel containing the table (if applicable), description, and data fields
     *
     * @return JPanel containing the table (if applicable), description, and data fields
     *********************************************************************************************/
    protected JPanel getFieldPanel()
    {
        return inputPnl;
    }

    /**********************************************************************************************
     * Get the reference to the undoable field panel containing the data fields
     *
     * @return Reference to the undoable field panel containing data fields
     *********************************************************************************************/
    protected UndoableDataFieldPanel getUndoFieldPanel()
    {
        return undoFieldPnl;
    }

    /**********************************************************************************************
     * Get the description and data field undo/redo manager
     *
     * @return Reference to the description and data field undo/redo manager
     *********************************************************************************************/
    protected CcddUndoManager getFieldPanelUndoManager()
    {
        return undoManager;
    }

    /**********************************************************************************************
     * Set the references to the description and data field undo/redo manager and handler
     *
     * @param undoManager
     *            undo/redo manager
     *
     * @param undoHandler
     *            undoable component handler
     *********************************************************************************************/
    protected void setEditPanelUndo(CcddUndoManager undoManager, CcddUndoHandler undoHandler)
    {
        this.undoManager = undoManager;
        this.undoHandler = undoHandler;
    }

    /**********************************************************************************************
     * Get a reference to the owner of the description and data field panel handler
     *
     * @return Reference to the owner of the description and data field panel handler
     *********************************************************************************************/
    protected Component getOwner()
    {
        return fieldPnlHndlrOwner;
    }

    /**********************************************************************************************
     * Get the name of the owner of this description and data field panel handler
     *
     * @return Name of the owner of this description and data field panel handler
     *********************************************************************************************/
    protected String getOwnerName()
    {
        return ownerName;
    }

    /**********************************************************************************************
     * Set the name of the owner of this description and data field panel handler
     *
     * @param ownerName
     *            name of the owner of this description and data field panel handler
     *********************************************************************************************/
    protected void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    /**********************************************************************************************
     * Placeholder for the method to update the owning dialog's change indicator
     *********************************************************************************************/
    protected abstract void updateOwnerChangeIndicator();

    /**********************************************************************************************
     * Get the description field text
     *
     * @return Description field text
     *********************************************************************************************/
    protected String getDescription()
    {
        return descriptionFld.getText().trim();
    }

    /**********************************************************************************************
     * Set the description field text
     *
     * @param description
     *            description field text
     *********************************************************************************************/
    protected void setDescription(String description)
    {
        descriptionFld.setText(description);
    }

    /**********************************************************************************************
     * Check if the description field is the current focus owner (i.e., editing is active for the
     * field)
     *
     * @return true if the description field has the focus
     *********************************************************************************************/
    protected boolean isDescriptionFocusOwner()
    {
        return descriptionFld.isFocusOwner();
    }

    /**********************************************************************************************
     * Enable/disable the description field, set its background color based on the enable status,
     * and set the description text
     *
     * @param enable
     *            true to enable editing the description, false to disable
     *
     * @param description
     *            text to place in the description field
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Update the description field text. Put the edit on the undo/redo stack based on the input
     * flag
     *
     * @param undoable
     *            true to enable putting the edit on the undo/redo stack
     *********************************************************************************************/
    protected void updateDescriptionField(boolean undoable)
    {
        descriptionFld.updateText(undoable);
    }

    /**********************************************************************************************
     * Get the width of the widest data field (including its label), plus padding on either side
     *
     * @return Width of the widest data field (including its label), plus padding on either side
     *********************************************************************************************/
    protected int getMaxFieldWidth()
    {
        return maxFieldWidth + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
    }

    /**********************************************************************************************
     * Get the data field information used when creating the data field panel
     *
     * @return List of the data field information used to build the panel
     *********************************************************************************************/
    protected List<FieldInformation> getPanelFieldInformation()
    {
        return fieldInformation;
    }

    /**********************************************************************************************
     * Update the field information values to match the data field component values
     *
     * @param fieldInformation
     *            data field information list
     *********************************************************************************************/
    protected void updateFieldValueFromComponent(List<FieldInformation> fieldInformation)
    {
        // Step through each data field
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Get a reference to the data field's input field to shorten subsequent calls
            Component inputFld = fieldInfo.getInputFld();

            // Check if the data field isn't a page format field (line break or separator) and the
            // field is applicable
            if (fieldInfo.getInputType().getInputFormat() != InputTypeFormat.PAGE_FORMAT
                && (fieldHandler.isFieldApplicable(fieldInfo.getOwnerName(),
                                                   fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                   null)))
            {
                // Check if this is a boolean input (check box) data field
                if (fieldInfo.getInputType().getInputFormat() == InputTypeFormat.BOOLEAN)
                {
                    // Update the data field information value with the current check box selection
                    // state
                    fieldInfo.setValue(((JCheckBox) inputFld).isSelected()
                                                                           ? "true"
                                                                           : "false");
                }
                // Check if the the field is a text field/area
                else if (inputFld instanceof JTextComponent)
                {
                    // Update the data field information value with the current text field/area
                    // value
                    fieldInfo.setValue(((JTextComponent) inputFld).getText());
                }
                // Check if the the field is a combo box
                else if (inputFld instanceof PaddedComboBox)
                {
                    // Update the data field information value with the current combo box selection
                    fieldInfo.setValue(((PaddedComboBox) inputFld).getSelectedItem().toString());
                }
            }
        }
    }

    /**********************************************************************************************
     * Update the data field component values to match the field information values
     *
     * @param fieldInformation
     *            data field information list
     *********************************************************************************************/
    protected void updateFieldComponentFromValue(List<FieldInformation> fieldInformation)
    {
        // Step through each data field
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Check if a text field or check box exists for this data field and isn't a page
            // format field (line break or separator)
            if (fieldInfo.getInputFld() != null
                && fieldInfo.getInputType().getInputFormat() != InputTypeFormat.PAGE_FORMAT)
            {
                // Check if this is a boolean input (check box) data field
                if (fieldInfo.getInputType().getInputFormat() == InputTypeFormat.BOOLEAN)
                {
                    // Update the data field check box selection state with the current field
                    // information value
                    ((JCheckBox) fieldInfo.getInputFld()).setSelected(Boolean.parseBoolean(fieldInfo.getValue()));
                }
                // Check if the the field is a text field/area
                else if (fieldInfo.getInputFld() instanceof JTextComponent)
                {
                    // Update the data field text field/area value with the current field
                    // information value
                    ((JTextComponent) fieldInfo.getInputFld()).setText(fieldInfo.getValue());
                }
                // Check if the the field is a combo box
                else if (fieldInfo.getInputFld() instanceof PaddedComboBox)
                {
                    // Update the data field combo box selection with the current field information
                    // value
                    ((PaddedComboBox) fieldInfo.getInputFld()).setSelectedItem(fieldInfo.getValue());
                }
            }
        }
    }

    /**********************************************************************************************
     * Create the table input field panel
     *
     * @param ccddMain
     *            main class reference
     *
     * @param fieldPnlHndlrOwner
     *            reference to the owner of this description and data field handler
     *
     * @param upperPane
     *            component to display above the description and data fields scroll pane; null if
     *            this field panel handler does not contain a table
     *
     * @param ownerName
     *            name of the owner of this field panel handler; null if no owner name is
     *            associated with it
     *
     * @param description
     *            description field text; null if the description is initially blank and disabled
     *
     * @param ownerFieldInfo
     *            list of field information to use to build the data fields; null if no data field
     *            is associated with owner
     *********************************************************************************************/
    protected void createDescAndDataFieldPanel(CcddMain ccddMain,
                                               final Component fieldPnlHndlrOwner,
                                               final JComponent upperPane,
                                               String ownerName,
                                               String description,
                                               List<FieldInformation> ownerFieldInfo)
    {
        final JScrollPane inputScrollPane;
        JPanel tablePnl = null;
        this.fieldInformation = new ArrayList<FieldInformation>();

        this.fieldPnlHndlrOwner = fieldPnlHndlrOwner;
        this.ownerName = ownerName;
        fieldHandler = ccddMain.getFieldHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();

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

        // Create borders for the input fields
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));
        Border emptyBorder = BorderFactory.createEmptyBorder();

        // Create an outer panel to put the editor panel in (the border doesn't appear without
        // this) and add the table description text field
        descAndFldPnl = new JPanel(new GridBagLayout());
        descAndFldPnl.setBorder(emptyBorder);
        inputPnl = new JPanel(new GridBagLayout());
        inputPnl.setBorder(emptyBorder);

        // Check if this editor contains an upper pane
        if (upperPane != null)
        {
            // Define the editor panel to contain the upper pane
            JPanel innerPanel = new JPanel();
            innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
            innerPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            innerPanel.add(upperPane);
            tablePnl = new JPanel(new GridBagLayout());
            tablePnl.add(innerPanel, gbc);
        }

        // Create a panel to hold the table's system name, description and, if applicable, message
        // ID information
        JPanel descriptionPnl = new JPanel(new GridBagLayout());

        // Create the description label
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        descriptionLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());

        // Check if this editor doesn't contain an upper pane
        if (upperPane == null)
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
        descriptionFld = undoHandler.new UndoableTextArea(3, 1);
        descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setEditable(true);
        descriptionFld.setLineWrap(true);
        descriptionFld.setWrapStyleWord(true);
        descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

        descScrollPane = new JScrollPane(descriptionFld);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;

        // Check if the description field is initially disabled
        if (description == null)
        {
            // Set the description field and description field scroll pane background color to
            // indicate these are disabled
            descriptionFld.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
            descScrollPane.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        }
        else
        {
            // Set the description field and description field scroll pane background color to
            // indicate these are enabled, and set the description field text
            descriptionFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            descScrollPane.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            descriptionFld.setText(description);
        }

        // Check if this editor doesn't contain an upper pane
        if (upperPane == null)
        {
            // Place the description field within a scroll pane and add the field to the editor
            descScrollPane.setBorder(border);
            descScrollPane.setMinimumSize(descScrollPane.getPreferredSize());
            gbc.gridy++;
            descriptionPnl.add(descScrollPane, gbc);
        }
        // The editor contains an upper pane
        else
        {
            // Place the description field within a scroll pane and add the field to the editor
            descriptionFld.setToolTipText(CcddUtilities.wrapText("Table description",
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            descScrollPane.setBorder(emptyBorder);
            descScrollPane.setViewportBorder(border);
            descScrollPane.setMinimumSize(descScrollPane.getPreferredSize());
            gbc.gridx++;
            descriptionPnl.add(descScrollPane, gbc);
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        }

        // Add the description panel to the editor
        gbc.gridx = 0;
        gbc.gridy++;
        descAndFldPnl.add(descriptionPnl, gbc);

        // Add the data field panel to the editor
        gbc.gridy++;
        gbc.insets.top = 0;
        gbc.insets.bottom = 0;
        createDataFieldPanel(false, CcddFieldHandler.getFieldInformationCopy(ownerFieldInfo));
        gbc.weighty = 1.0;

        // Check if this editor doesn't contain an upper pane
        if (upperPane == null)
        {
            // Add an invisible component in order to force the description panel and data fields
            // to the top of the panel
            JLabel invisibleLbl = new JLabel("");
            gbc.gridy++;
            descAndFldPnl.add(invisibleLbl, gbc);
        }

        // Create the scroll pane to hold the description and data fields
        inputScrollPane = new JScrollPane(descAndFldPnl);
        inputScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inputScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        inputScrollPane.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

        // Check if the data field panel exists (if there are no fields defined then the panel
        // isn't created)
        if (fieldPnl != null)
        {
            // Set the data field panel size so that the input scroll pane is sized such that
            // the fields are displayed
            fieldPnl.setSize(fieldPnl.getPreferredSize());
        }

        // Set the input scroll pane's minimum height to that of the description field
        inputScrollPane.setPreferredSize(inputScrollPane.getPreferredSize());
        inputScrollPane.setMinimumSize(new Dimension(inputScrollPane.getMinimumSize().width,
                                                     descriptionPnl.getPreferredSize().height
                                                                                             + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing()
                                                                                               * 2));

        // Check if this editor doesn't contain an upper pane
        if (upperPane == null)
        {
            // Add the input panel to the editor panel
            inputPnl.add(inputScrollPane, gbc);
        }
        // The editor contains an upper pane
        else
        {
            // Place the table and input panels into a split pane
            CustomSplitPane splitPane = new CustomSplitPane(tablePnl,
                                                            inputScrollPane,
                                                            null,
                                                            JSplitPane.VERTICAL_SPLIT);

            // Set so that when resizing the editor's vertical size the table gets all the
            // additional height
            splitPane.setResizeWeight(1.0);

            // Add the split pane to the editor panel
            inputPnl.add(splitPane, gbc);
        }

        // Add a listener for changes in the input panel's size
        inputScrollPane.addComponentListener(new ComponentAdapter()
        {
            /**************************************************************************************
             * Handle resizing of the editor panel
             *************************************************************************************/
            @Override
            public void componentResized(ComponentEvent ce)
            {
                // Create a runnable object to be executed
                SwingUtilities.invokeLater(new Runnable()
                {
                    /******************************************************************************
                     * Since the size returned by get___Size() can lag the actual size, use
                     * invokeLater to let the sizes "catch up"
                     *****************************************************************************/
                    @Override
                    public void run()
                    {
                        // Check if the field panel exists (for some dialogs the panel exists only
                        // if a selection is made to determine which fields to display)
                        if (fieldPnl != null)
                        {
                            // TODO IF THE VERTICAL SCROLL BAR IS VISIBLE IN THE INPUT SCROLL PANE
                            // THEN A SEPARATOR 'JUMPS' IN WIDTH AS THE PANEL IS RESIZED. IT
                            // APPEARS THAT THE WRAP LAYOUT METHOD GETS CALLED WITH THE CORRECT
                            // SIZE AND THEN A SMALLER ONE (16 pixels LESS)

                            // Update the field panel width to the input scroll pane width to that
                            // the field layout (wrapped) is performed
                            fieldPnl.setSize(new Dimension(inputScrollPane.getWidth()
                                                           -
                                                           (inputScrollPane.getVerticalScrollBar()
                                                                           .isShowing()
                                                                                        ? LAF_SCROLL_BAR_WIDTH
                                                                                        : 0)
                                                           - ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                           fieldPnl.getHeight()));
                        }

                        // Revalidate to force the input panel to redraw to the new size
                        inputScrollPane.revalidate();
                    }
                });
            }
        });
    }

    /**********************************************************************************************
     * Create the data fields for display in the description and data field panel
     *
     * @param undoable
     *            true if the change(s) to the data fields should be stored for possible undo/redo
     *            operations; false to not store the changes
     *
     * @param ownerFieldInfo
     *            list of field information to use to build the data fields null if no data field
     *            is associated with the owner
     *********************************************************************************************/
    protected void createDataFieldPanel(boolean undoable, List<FieldInformation> ownerFieldInfo)
    {
        maxFieldWidth = 0;

        // Check if any data fields are provided for this input panel
        if (ownerFieldInfo != null)
        {
            // Check if the supplied fields aren't already loaded as the input panel's fields. If
            // these are the same there's no need to copy the information (plus the clear command
            // below would wipe out the supplied information)
            if (!ownerFieldInfo.equals(fieldInformation))
            {
                // Clear the current field information. Clearing (versus recreating) the list is
                // done so the the variable's reference isn't changed
                fieldInformation.clear();

                // Step through each of the supplied data field's information
                for (FieldInformation fldInfo : ownerFieldInfo)
                {
                    // Add the data field information to the list
                    fieldInformation.add(fldInfo);
                }
            }
        }
        // No data field information is supplied
        else
        {
            // Clear the current field information. Clearing (versus recreating) the list is done
            // so the the variable's reference isn't changed
            fieldInformation.clear();
        }

        // Set the preferred size so that the layout manager uses its default sizing
        fieldPnlHndlrOwner.setPreferredSize(null);

        // Check if the data fields are already displayed
        if (fieldPnl != null)
        {
            // Remove the existing data fields
            descAndFldPnl.remove(fieldPnl);
        }

        // Check if any data fields exist for this table/group/etc.
        if (!fieldInformation.isEmpty())
        {
            // Create a panel to contain the data fields. As the editor is resized the field panel
            // is resized to contain the data fields, wrapping them to new lines as needed
            fieldPnl = new JPanel(new WrapLayout(FlowLayout.LEADING));

            // Adjust the border to align the first field with the description label
            fieldPnl.setBorder(BorderFactory.createEmptyBorder(-ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                               -ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                               0,
                                                               0));

            // Step through each data field
            for (final FieldInformation fieldInfo : fieldInformation)
            {
                // Check if this field is applicable
                if (fieldHandler.isFieldApplicable(fieldInfo.getOwnerName(),
                                                   fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                   null))
                {
                    FieldInformation fldInfo;

                    switch (fieldInfo.getInputType().getInputFormat())
                    {
                        case PAGE_FORMAT:
                            if (fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.BREAK)))
                            {
                                // Create an undoable text field
                                UndoableTextField undoableTxtFld = undoHandler.new UndoableTextField(fieldInfo.getValue(),
                                                                                                     fieldInfo.getSize());

                                // Get the reference to the data field in the field handler
                                fldInfo = fieldHandler.getFieldInformationByName(fieldInfo.getOwnerName(),
                                                                                 fieldInfo.getFieldName());

                                // Check if the data field exists
                                if (fldInfo != null)
                                {
                                    // Store the input field in the field handler
                                    fldInfo.setInputFld(undoableTxtFld);
                                }

                                // Store the reference to the input field in the data field
                                // information
                                fieldInfo.setInputFld(undoableTxtFld);

                                // Add a vertical separator to the field panel
                                fieldPnl.add(new JSeparator(SwingConstants.VERTICAL));
                            }
                            else if (fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.SEPARATOR)))
                            {
                                // Create an undoable text field
                                UndoableTextField undoableTxtFld = undoHandler.new UndoableTextField(fieldInfo.getValue(),
                                                                                                     fieldInfo.getSize());

                                // Get the reference to the data field in the field handler
                                fldInfo = fieldHandler.getFieldInformationByName(fieldInfo.getOwnerName(),
                                                                                 fieldInfo.getFieldName());

                                // Check if the data field exists
                                if (fldInfo != null)
                                {
                                    // Store the input field in the field handler
                                    fldInfo.setInputFld(undoableTxtFld);
                                }

                                // Store the reference to the input field in the data field
                                // information
                                fieldInfo.setInputFld(undoableTxtFld);

                                // Add a horizontal separator to the field panel
                                fieldPnl.add(new JSeparator());
                            }

                            break;

                        case BOOLEAN:
                            // Create an undoable text field
                            UndoableCheckBox undoableChkBox = undoHandler.new UndoableCheckBox(fieldInfo.getFieldName(),
                                                                                               Boolean.valueOf(fieldInfo.getValue()));

                            // Set the field's value ('true' or 'false')
                            fieldInfo.setValue(String.valueOf(Boolean.parseBoolean(fieldInfo.getValue())));

                            // Get the reference to the data field in the field handler
                            fldInfo = fieldHandler.getFieldInformationByName(fieldInfo.getOwnerName(),
                                                                             fieldInfo.getFieldName());

                            // Check if the data field exists
                            if (fldInfo != null)
                            {
                                // Store the input field in the field handler
                                fldInfo.setInputFld(undoableChkBox);
                            }

                            // Store the reference to the input field in the data field
                            // information
                            fieldInfo.setInputFld(undoableChkBox);

                            // Set the data field reference in the undo handler for the
                            // input field
                            undoableChkBox.setUndoFieldInformation(fieldInfo.getOwnerName(),
                                                                   fieldInfo.getFieldName());

                            // Set the check box label font and color
                            UndoableCheckBox booleanCb = undoableChkBox;
                            booleanCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                            booleanCb.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());

                            // Adjust the left and right padding around the check box so that it is
                            // spaced the same as a text field data field
                            booleanCb.setBorder(BorderFactory.createEmptyBorder(0,
                                                                                ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                                0,
                                                                                ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()));

                            // Check if a description exists for this field
                            if (!fieldInfo.getDescription().isEmpty())
                            {
                                // Set the description as the tool tip text for this check box
                                booleanCb.setToolTipText(CcddUtilities.wrapText(fieldInfo.getDescription(),
                                                                                ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                            }

                            // And the check box to the field panel
                            fieldPnl.add(booleanCb);

                            // Store this check box's width if it is the largest data field width
                            maxFieldWidth = Math.max(maxFieldWidth,
                                                     booleanCb.getPreferredSize().width);

                            break;

                        default:
                            final JComponent inputFld;

                            // Create a panel for a single label and text field pair. This is
                            // necessary so that the two will stay together if line wrapping occurs
                            // due to a window size change
                            JPanel singleFldPnl = new JPanel(new FlowLayout(FlowLayout.LEADING,
                                                                            ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                            ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 4));
                            singleFldPnl.setBorder(BorderFactory.createEmptyBorder());

                            // Create the data field label
                            JLabel fieldLbl = new JLabel(fieldInfo.getFieldName());
                            fieldLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                            fieldLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                            singleFldPnl.add(fieldLbl);

                            // Check if the input type is for multi-line text
                            if (fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT_MULTI))
                                || fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT_MULTI_WHT_SPC)))
                            {
                                // Create an undoable text area
                                UndoableTextArea undoableTxtArea = undoHandler.new UndoableTextArea(fieldInfo.getValue(),
                                                                                                    ModifiableSizeInfo.MAX_DATA_FIELD_ROWS.getSize(),
                                                                                                    fieldInfo.getSize());

                                // Set the text area characteristics
                                undoableTxtArea.setEditable(true);
                                undoableTxtArea.setLineWrap(true);
                                undoableTxtArea.setWrapStyleWord(true);
                                undoableTxtArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
                                undoableTxtArea.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

                                // Get the reference to the data field in the field handler
                                fldInfo = fieldHandler.getFieldInformationByName(fieldInfo.getOwnerName(),
                                                                                 fieldInfo.getFieldName());

                                // Check if the data field exists
                                if (fldInfo != null)
                                {
                                    // Store the input field in the field handler
                                    fldInfo.setInputFld(undoableTxtArea);
                                }

                                // Store the reference to the input field in the data field
                                // information
                                fieldInfo.setInputFld(undoableTxtArea);

                                inputFld = undoableTxtArea;
                            }
                            // The input type is one other than for multi-line text
                            else
                            {
                                // Check if the data field has no selection items; i.e., it is
                                // displayed as a text input field and not a combo box
                                if (fieldInfo.getInputType().getInputItems() == null)
                                {
                                    // Create an undoable text field
                                    UndoableTextField undoableTxtFld = undoHandler.new UndoableTextField(fieldInfo.getValue(),
                                                                                                         fieldInfo.getSize());

                                    // Get the reference to the data field in the field handler
                                    fldInfo = fieldHandler.getFieldInformationByName(fieldInfo.getOwnerName(),
                                                                                     fieldInfo.getFieldName());

                                    // Check if the data field exists
                                    if (fldInfo != null)
                                    {
                                        // Store the input field in the field handler
                                        fldInfo.setInputFld(undoableTxtFld);
                                    }

                                    // Store the reference to the input field in the data field
                                    // information
                                    fieldInfo.setInputFld(undoableTxtFld);

                                    // Set the data field reference in the undo handler for the
                                    // input field
                                    undoableTxtFld.setUndoFieldInformation(fieldInfo.getOwnerName(),
                                                                           fieldInfo.getFieldName());

                                    inputFld = undoableTxtFld;
                                }
                                // The field has list items; display it as a combo box
                                else
                                {
                                    // Create an undoable combo box
                                    UndoableComboBox undoableCmbBx = undoHandler.new UndoableComboBox(fieldInfo.getInputType()
                                                                                                               .getInputItems()
                                                                                                               .toArray(new String[0]),
                                                                                                      ModifiableFontInfo.INPUT_TEXT.getFont());

                                    // Get the reference to the data field in the field handler
                                    fldInfo = fieldHandler.getFieldInformationByName(fieldInfo.getOwnerName(),
                                                                                     fieldInfo.getFieldName());

                                    // Check if the data field exists
                                    if (fldInfo != null)
                                    {
                                        // Store the input field in the field handler
                                        fldInfo.setInputFld(undoableCmbBx);
                                    }

                                    // Store the reference to the input field in the data field
                                    // information
                                    fieldInfo.setInputFld(undoableCmbBx);

                                    // Set the data field reference in the undo handler for the
                                    // input field
                                    undoableCmbBx.setUndoFieldInformation(fieldInfo.getOwnerName(),
                                                                          fieldInfo.getFieldName());

                                    inputFld = undoableCmbBx;

                                    // Enable item matching and set the initially selected item
                                    ((UndoableComboBox) inputFld).enableItemMatching(null);
                                    ((UndoableComboBox) inputFld).setSelectedItem(fieldInfo.getValue(),
                                                                                  false);
                                }
                            }

                            // Set the input field colors
                            inputFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                            inputFld.setBackground(fieldInfo.getValue().isEmpty()
                                                   && fieldInfo.isRequired()
                                                                             ? ModifiableColorInfo.REQUIRED_BACK.getColor()
                                                                             : ModifiableColorInfo.INPUT_BACK.getColor());

                            // Check if a description exists for this field
                            if (!fieldInfo.getDescription().isEmpty())
                            {
                                // Set the description as the tool tip text for this input field
                                inputFld.setToolTipText(CcddUtilities.wrapText(fieldInfo.getDescription(),
                                                                               ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                            }

                            // Check if this is a multi-line text area field
                            if (inputFld instanceof UndoableTextArea)
                            {
                                // Create a scroll pane to display the text area
                                JScrollPane scrollPane = new JScrollPane(inputFld);
                                inputFld.setBorder(BorderFactory.createEmptyBorder());
                                scrollPane.setBorder(BorderFactory.createEmptyBorder());
                                scrollPane.setViewportBorder(border);

                                // Add the data field to the single field panel
                                singleFldPnl.add(scrollPane);
                            }
                            // This is a single line field
                            else
                            {
                                // Add the data field to the single field panel
                                singleFldPnl.add(inputFld);
                            }

                            // And the single field to the field panel
                            fieldPnl.add(singleFldPnl);

                            // Store this field's width if it is the largest data field width
                            maxFieldWidth = Math.max(maxFieldWidth,
                                                     singleFldPnl.getPreferredSize().width);

                            // Check if the input field is a text component
                            if (inputFld instanceof JTextComponent)
                            {
                                inputFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                                ((JTextComponent) inputFld).setEditable(true);

                                // Check if this is a single line field
                                if (!(inputFld instanceof UndoableTextArea))
                                {
                                    inputFld.setBorder(border);
                                }

                                // Create an input field verifier for the data field
                                inputFld.setInputVerifier(new InputVerifier()
                                {
                                    // Storage for the last valid value entered; used to restore
                                    // the data field value if an invalid value is entered.
                                    // Initialize to the value at the time the field is created
                                    String lastValid = ((JTextComponent) inputFld).getText();

                                    /**************************************************************
                                     * Verify the contents of a the data field
                                     *************************************************************/
                                    @Override
                                    public boolean verify(JComponent input)
                                    {
                                        boolean isValid = true;

                                        // Get the data field reference to shorten subsequent calls
                                        JTextComponent inFld = (JTextComponent) input;

                                        // Get the data field contents
                                        String inputTxt = inFld.getText();

                                        // Check if the field's input type doesn't allow leading
                                        // and trailing white space characters
                                        if (!fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT_WHT_SPC))
                                            && !fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT_MULTI_WHT_SPC)))
                                        {
                                            // Remove leading and trailing white space characters
                                            inputTxt = inputTxt.trim();
                                        }

                                        // Check if the field contains an illegal character
                                        if (!fieldInfo.getInputType().getInputMatch().isEmpty()
                                            && !inputTxt.isEmpty()
                                            && !inputTxt.matches(fieldInfo.getInputType().getInputMatch()))
                                        {
                                            // Inform the user that the data field contents is
                                            // invalid
                                            new CcddDialogHandler().showMessageDialog(fieldPnlHndlrOwner,
                                                                                      "<html><b>Invalid characters in field '</b>"
                                                                                                          + fieldInfo.getFieldName()
                                                                                                          + "<b>'; characters consistent with input type '</b>"
                                                                                                          + fieldInfo.getInputType().getInputName()
                                                                                                          + "<b>' expected",
                                                                                      "Invalid "
                                                                                                                             + fieldInfo.getInputType().getInputName(),
                                                                                      JOptionPane.WARNING_MESSAGE,
                                                                                      DialogOption.OK_OPTION);

                                            // Toggle the controls enable status so that the
                                            // buttons are redrawn correctly
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
                                                // Restore the previous value in the data field
                                                ((UndoableTextField) inFld).setText(lastValid, false);
                                            }
                                            // Check if the data field is a text area (multi-line)
                                            else if (input instanceof UndoableTextArea)
                                            {
                                                // Restore the previous value in the data field
                                                ((UndoableTextArea) inFld).setText(lastValid);
                                            }

                                            // Set the flag to indicate an invalid value was
                                            // entered
                                            isValid = false;
                                        }
                                        // The input is valid
                                        else
                                        {
                                            // Store the 'cleaned' text back into the text field.
                                            // For numeric types, reformat the input value
                                            inFld.setText(fieldInfo.getInputType().formatInput(inputTxt));
                                            fieldInfo.setValue(inFld.getText());

                                            // Store the new value as the last valid value
                                            lastValid = inFld.getText();

                                            // Set the text field background color. If the field is
                                            // empty and is flagged as required then set the
                                            // background to indicate a value should be supplied
                                            setFieldBackground(fieldInfo);
                                        }

                                        return isValid;
                                    }
                                });
                            }

                            break;
                    }
                }
            }

            // Check that at least one field exists
            if (fieldPnl.getComponentCount() != 0)
            {
                // Add the data field panel to the dialog
                descAndFldPnl.add(fieldPnl, gbc);
            }
        }

        // Check if the data field panel change should be put in the undo/redo stack
        if (undoable)
        {
            // Store the field information in the undo handler in case the update needs to be
            // undone
            undoFieldPnl.addDataFieldEdit(this,
                                          CcddFieldHandler.getFieldInformationCopy(fieldInformation));
        }

        // Force the owner of the editor panel to redraw so that changes to the fields are
        // displayed and the owner's size is adjusted
        fieldPnlHndlrOwner.revalidate();
        fieldPnlHndlrOwner.repaint();
    }

    /**********************************************************************************************
     * Clear the values from all fields
     *********************************************************************************************/
    protected void clearFieldValues()
    {
        // Disable automatically ending the edit sequence. This allows all of the cleared fields to
        // be grouped into a single sequence so that if undone, all fields are restored
        undoHandler.setAutoEndEditSequence(false);

        // Step through each data field
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Check if this is a boolean input (check box) data field
            if (fieldInfo.getInputType().getInputFormat() == InputTypeFormat.BOOLEAN)
            {
                // Set the field value to 'false'
                fieldInfo.setValue("false");

                // Set the check box
                ((UndoableCheckBox) fieldInfo.getInputFld()).setSelected(false);
            }
            // Not a boolean input (check box) data field
            else
            {
                // Clear the field value
                fieldInfo.setValue("");

                // Check if the field is applicable (i.e., displayed). A field that isn't
                // applicable has a null input field
                if (fieldInfo.getInputFld() != null)
                {
                    // Check if the input field is a text component
                    if (fieldInfo.getInputFld() instanceof JTextComponent)
                    {
                        // Blank the text field
                        ((JTextComponent) fieldInfo.getInputFld()).setText("");
                    }
                    // Check if the input field is a combo box
                    else if (fieldInfo.getInputFld() instanceof PaddedComboBox)
                    {
                        // Blank the combo box by selecting the blank item (which is always defined
                        // as the first item in the list)
                        ((PaddedComboBox) fieldInfo.getInputFld()).setSelectedItem("");
                    }

                    // Set the text field background color. If the field is flagged as required
                    // then set the background to indicate a value should be supplied
                    setFieldBackground(fieldInfo);
                }
            }
        }

        // Re-enable automatic edit sequence ending, then end the edit sequence to group the
        // cleared fields
        undoHandler.setAutoEndEditSequence(true);
        undoManager.endEditSequence();
    }

    /**********************************************************************************************
     * Set the data field background color for all fields based each field's value and required
     * flag
     *********************************************************************************************/
    protected void setFieldBackgound()
    {
        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /**************************************************************************************
             * Set the data field colors after other pending events are complete. If this isn't
             * done following other events then the colors aren't updated consistently
             *************************************************************************************/
            @Override
            public void run()
            {
                // Step through each field
                for (FieldInformation fieldInfo : fieldInformation)
                {
                    // Check if the field is applicable and isn't a boolean input (check box) data
                    // field
                    if (fieldInfo.getInputFld() != null
                        && fieldInfo.getInputType().getInputFormat() != InputTypeFormat.BOOLEAN)
                    {
                        // Set the text field background color. If the field is empty and is
                        // flagged as required then set the background to indicate a value should
                        // be supplied
                        setFieldBackground(fieldInfo);
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Set the specified data field's background color based the field's value and required flag
     *
     * @param fieldInfo
     *            reference to the data field's information
     *********************************************************************************************/
    private void setFieldBackground(FieldInformation fieldInfo)
    {
        // Set the text field background color. If the field is empty and is flagged as required
        // then set the background to indicate a value should be supplied
        ((JComponent) fieldInfo.getInputFld()).setBackground(fieldInfo.getValue().isEmpty()
                                                             && fieldInfo.isRequired()
                                                                                       ? ModifiableColorInfo.REQUIRED_BACK.getColor()
                                                                                       : ModifiableColorInfo.INPUT_BACK.getColor());
    }
}
