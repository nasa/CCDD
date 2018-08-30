/**
 * CFS Command and Data Dictionary program preferences dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;
import static CCDD.CcddConstants.RADIO_BUTTON_CHANGE_EVENT;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import CCDD.CcddClassesComponent.ColorCheckBox;
import CCDD.CcddClassesComponent.DnDTabbedPane;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesComponent.JFontChooser;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.GUIUpdateType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary program preferences dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddPreferencesDialog extends CcddDialogHandler
{
    // Class reference
    private final CcddMain ccddMain;

    // Components referenced by multiple methods
    private DnDTabbedPane tabbedPane;
    private Border emptyBorder;
    private JButton btnClose;
    private JTextField[] sizeFld;
    private JTextField[] spacingFld;
    private JTextField[] pathFld;
    private JTextField[] otherFld;

    // Maximum height, in pixels, based on all of the individual tabs' scroll panes
    private int maxScrollPaneHeight;

    // Tab identifiers
    private final String LAF = "L&F";
    private final String FONT = "Font";
    private final String COLOR = "Color";
    private final String SIZE = "Size";
    private final String SPACING = "Spacing";
    private final String PATH = "Path";
    private final String OTHER = "Other";

    /**********************************************************************************************
     * Program preferences dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddPreferencesDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create the preferences dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the preferences dialog
     *********************************************************************************************/
    private void initialize()
    {
        maxScrollPaneHeight = 0;

        // Create an empty border for use with the dialog components
        emptyBorder = BorderFactory.createEmptyBorder();

        // Create a tabbed pane
        tabbedPane = new DnDTabbedPane(SwingConstants.TOP);
        tabbedPane.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());

        // Add the tabs to the tabbed pane
        addLafTab();
        addFontTab();
        addColorTab();
        addSizeTab();
        addSpacingTab();
        addPathTab();
        addOtherTab();

        // Create a panel for the preference dialog buttons
        JPanel buttonPnl = new JPanel();

        // Update button
        final JButton btnUpdateAll = CcddButtonPanelHandler.createButton("Update",
                                                                         STORE_ICON,
                                                                         KeyEvent.VK_U,
                                                                         "Update the program preference values");

        // Add a listener for the Update button
        btnUpdateAll.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Update the program preference values
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                int index = 0;

                // Base the action on the name of the tab selected
                switch (tabbedPane.getTitleAt(tabbedPane.getSelectedIndex()))
                {
                    case LAF:
                    case FONT:
                    case COLOR:
                        // These tabs do not display the Update button, but instead either act
                        // immediately or have a secondary dialog that controls updating the
                        // preference value
                        break;

                    case SIZE:
                        // Update the program size preference values. Step through each modifiable
                        // size
                        for (ModifiableSizeInfo modSize : ModifiableSizeInfo.values())
                        {
                            // Get the current value from the size text field
                            int currentValue = Integer.valueOf(sizeFld[index].getText());

                            // Check if the size has changed
                            if (modSize.getSize() != currentValue)
                            {
                                // Update the size to the new value
                                modSize.setSize(currentValue, ccddMain.getProgPrefs());
                            }

                            index++;
                        }

                        break;

                    case SPACING:
                        // Update the program spacing preference values. Step through each
                        // modifiable spacing
                        for (ModifiableSpacingInfo modSpacing : ModifiableSpacingInfo.values())
                        {
                            // Get the current value from the spacing text field
                            int currentValue = Integer.valueOf(spacingFld[index].getText());

                            // Check if the spacing has changed
                            if (modSpacing.getSpacing() != currentValue)
                            {
                                // Update the spacing to the new value
                                modSpacing.setSpacing(currentValue, ccddMain.getProgPrefs());
                            }

                            index++;
                        }

                        break;

                    case PATH:
                        // Update the program path preference. Step through each modifiable path
                        for (ModifiablePathInfo modPath : ModifiablePathInfo.values())
                        {
                            // Get the current path from the path text field
                            String currentPath = pathFld[index].getText().trim();

                            // Check if the path has changed
                            if (!modPath.getPath().equals(currentPath))
                            {
                                // Store the path in the program preferences backing store
                                CcddFileIOHandler.storePath(ccddMain,
                                                            currentPath,
                                                            false,
                                                            modPath);
                            }

                            index++;
                        }

                        break;

                    case OTHER:
                        // Update the other setting preference. Step through each modifiable other
                        // setting
                        for (ModifiableOtherSettingInfo modOther : ModifiableOtherSettingInfo.values())
                        {
                            // Get the current setting value from the other setting text field
                            String currentValue = otherFld[index].getText().trim();

                            // Check if the setting value has changed
                            if (!modOther.getValue().equals(currentValue))
                            {
                                // Update the other setting to the new value
                                modOther.setValue(currentValue, ccddMain.getProgPrefs());
                            }

                            index++;
                        }

                        break;
                }
            }
        });

        // Close button
        JButton btnCloseDlg = CcddButtonPanelHandler.createButton("Close",
                                                                  CLOSE_ICON,
                                                                  KeyEvent.VK_C,
                                                                  "Close the program preferences dialog");

        // Add a listener for the Close button
        btnCloseDlg.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Close the program preferences dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Close the dialog
                closeDialog();
            }
        });

        // Listen for tab selection changes
        tabbedPane.addChangeListener(new ChangeListener()
        {
            /**************************************************************************************
             * Handle a tab selection change
             *************************************************************************************/
            @Override
            public void stateChanged(ChangeEvent ce)
            {
                // Get the index of the selected tab
                int tabIndex = tabbedPane.getSelectedIndex();

                // Check if a tab is selected
                if (tabIndex != -1)
                {
                    // Base the action of the name of the tab selected
                    switch (tabbedPane.getTitleAt(tabIndex))
                    {
                        case LAF:
                        case FONT:
                        case COLOR:
                            // Hide the update button
                            btnUpdateAll.setVisible(false);
                            break;

                        case SIZE:
                        case SPACING:
                        case PATH:
                        case OTHER:
                            // Show the update button
                            btnUpdateAll.setVisible(true);
                            break;
                    }
                }
            }
        });

        // Add the buttons to the dialog panel
        buttonPnl.add(btnUpdateAll);
        buttonPnl.add(btnCloseDlg);

        // Toggle the tab selection so that the first tab is selected and the Update button
        // visibility is set accordingly
        tabbedPane.setSelectedIndex(-1);
        tabbedPane.setSelectedIndex(0);

        // Set the initial size of the preferences dialog based on the individual panes' contents
        tabbedPane.setPreferredSize(new Dimension(tabbedPane.getPreferredSize().width
                                                  + LAF_SCROLL_BAR_WIDTH,
                                                  maxScrollPaneHeight));

        // Display the Preferences dialog
        showOptionsDialog(ccddMain.getMainFrame(),
                          tabbedPane,
                          buttonPnl,
                          btnCloseDlg,
                          "Preferences",
                          true);
    }

    /**********************************************************************************************
     * Add the look and feel update tab to the tabbed pane
     *********************************************************************************************/
    private void addLafTab()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a panel to contain the look & feel components
        JPanel lafPnl = new JPanel(new GridBagLayout());
        lafPnl.setBorder(emptyBorder);

        // Create an inner panel for component justification purposes
        JPanel innerPnl = new JPanel(new GridBagLayout());
        innerPnl.setBorder(emptyBorder);
        innerPnl.add(lafPnl, gbc);

        // Add an invisible component in order to force the look & feel selection panel to the left
        JLabel invisibleLbl = new JLabel("");
        gbc.weightx = 1.0;
        gbc.gridx++;
        innerPnl.add(invisibleLbl, gbc);

        // Use an outer panel so that the components can be forced to the top of the tab area
        JPanel outerPnl = new JPanel(new BorderLayout());
        outerPnl.setBorder(emptyBorder);
        outerPnl.add(innerPnl, BorderLayout.PAGE_START);

        // Add the look & feel selection tab to the tabbed pane
        tabbedPane.addTab(LAF, null, outerPnl, "Change program look & feel");

        // Obtain the list of available look & feels to use in creating the radio buttons
        LookAndFeelInfo[] lafInfo = UIManager.getInstalledLookAndFeels();

        // Check if any look & feels exist
        if (lafInfo.length != 0)
        {
            gbc.weightx = 0.0;
            gbc.gridx = 0;

            // Create storage for the look & feel descriptions
            String[][] lafDescriptions = new String[lafInfo.length][2];

            // Step through each look & feel
            for (int index = 0; index < lafInfo.length; index++)
            {
                // Store the look & feel name
                lafDescriptions[index][0] = lafInfo[index].getName();
            }

            // Create a panel containing a grid of radio buttons representing the look & feels from
            // which to choose
            addRadioButtons(ccddMain.getLookAndFeel(),
                            true,
                            lafDescriptions,
                            null,
                            "Select the application's 'look & feel'",
                            false,
                            lafPnl,
                            gbc);

            // Add a listener for radio button selection change events
            addPropertyChangeListener(new PropertyChangeListener()
            {
                /**********************************************************************************
                 * Handle a radio button selection change event
                 *********************************************************************************/
                @Override
                public void propertyChange(PropertyChangeEvent pce)
                {
                    // Check if the event indicates a radio button selection change
                    if (pce.getPropertyName().equals(RADIO_BUTTON_CHANGE_EVENT))
                    {
                        // Get the radio button selected
                        String buttonName = pce.getNewValue().toString();

                        // Check if the selected look & feel differs from the one currently in use
                        if (!ccddMain.getLookAndFeel().equals(buttonName))
                        {
                            // Save the selected look & feel name for storage in the program
                            // preferences backing store
                            ccddMain.setLookAndFeel(buttonName);

                            // Update the visible GUI components to the new look & feel
                            ccddMain.updateGUI(GUIUpdateType.LAF,
                                               new CcddDialogHandler[] {CcddPreferencesDialog.this});
                        }
                    }
                }
            });
        }
    }

    /**********************************************************************************************
     * Add the font update tab to the tabbed pane
     *********************************************************************************************/
    private void addFontTab()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a panel to contain the font components
        JPanel innerFontPnl = new JPanel(new GridBagLayout());
        innerFontPnl.setBorder(emptyBorder);

        // Use an outer panel so that the components can be forced to the top of the tab area
        JPanel fontPnl = new JPanel(new BorderLayout());
        fontPnl.setBorder(emptyBorder);
        fontPnl.add(innerFontPnl, BorderLayout.PAGE_START);

        // Create a scroll pane in which to display the font selection buttons
        JScrollPane fontScrollPane = new JScrollPane(fontPnl);
        fontScrollPane.setBorder(emptyBorder);
        fontScrollPane.setViewportBorder(emptyBorder);

        // Add the font selection tab to the tabbed pane
        tabbedPane.addTab(FONT, null, fontScrollPane, "Change program fonts");

        // Create a listener for the font button presses
        ActionListener fontBtnListener = new ActionListener()
        {
            /**************************************************************************************
             * Handle a font button press
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the reference to the modifiable font information based on the button name
                // (in which the modifiable font's program preferences key is stored)
                final ModifiableFontInfo modFont = ModifiableFontInfo.getModifiableFontInfo(((JButton) ae.getSource()).getName());

                // Create a font chooser
                final JFontChooser chooser = new JFontChooser();

                // Set the font chooser controls to reflect the modifiable font
                chooser.setSelectedFont(modFont.getFont());

                // Create the font choice dialog
                final CcddDialogHandler dialog = new CcddDialogHandler();

                // Add a listener for the Update button
                ActionListener okayAction = new ActionListener()
                {
                    /******************************************************************************
                     * Handle an Update button press event
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the font changed
                        if (!modFont.getFont().getFamily().equals(chooser.getSelectedFontFamily())
                            || modFont.getFont().getStyle() != chooser.getSelectedFontStyle()
                            || modFont.getFont().getSize() != chooser.getSelectedFontSize())
                        {
                            // Update the modifiable font information to the new font
                            updateFont(modFont,
                                       chooser.getSelectedFontFamily(),
                                       chooser.getSelectedFontStyle(),
                                       chooser.getSelectedFontSize(),
                                       new CcddDialogHandler[] {CcddPreferencesDialog.this,
                                                                dialog});
                        }
                    }
                };

                // Add a listener for the Default button
                ActionListener defaultAction = new ActionListener()
                {
                    /******************************************************************************
                     * Handle a Default button press event
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the font changed
                        if (!modFont.getFont().getFamily().equals(modFont.getDefaultFamily())
                            || modFont.getFont().getStyle() != modFont.getDefaultStyle()
                            || modFont.getFont().getSize() != modFont.getDefaultSize())
                        {
                            // Update the modifiable font information to its default font
                            updateFont(modFont,
                                       modFont.getDefaultFamily(),
                                       modFont.getDefaultStyle(),
                                       modFont.getDefaultSize(),
                                       new CcddDialogHandler[] {CcddPreferencesDialog.this,
                                                                dialog});
                        }

                        // Update the font chooser to the default font
                        chooser.setSelectedFont(modFont.getFont());
                    }
                };

                // Display the font selection dialog
                dialog.showOptionsDialog(CcddPreferencesDialog.this,
                                         chooser.createChooserPanel(),
                                         getButtonPanel(okayAction, defaultAction, dialog),
                                         btnClose,
                                         "Select Font: " + modFont.getName(),
                                         false);
            }
        };

        // Create storage for the buttons and sample text labels representing each modifiable font
        JButton[] fontBtn = new JButton[ModifiableFontInfo.values().length];
        JLabel[] fontLbl = new JLabel[ModifiableFontInfo.values().length];

        int index = 0;
        String[] fontStyles = new String[] {"Plain", "Bold", "Italic", "BoldItalic"};

        // Step through each modifiable font
        for (ModifiableFontInfo modFont : ModifiableFontInfo.values())
        {
            // Create a button and sample text label for the modifiable font, and add these to the
            // font panel
            fontBtn[index] = new JButton(modFont.getName());
            fontBtn[index].setName(modFont.getPreferenceKey());
            fontBtn[index].setToolTipText(CcddUtilities.wrapText(modFont.getDescription(),
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            fontBtn[index].setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
            fontBtn[index].addActionListener(fontBtnListener);
            innerFontPnl.add(fontBtn[index], gbc);
            fontLbl[index] = new JLabel("sample text: "
                                        + modFont.getFont().getFamily()
                                        + ", "
                                        + fontStyles[modFont.getFont().getStyle()]
                                        + ", "
                                        + modFont.getFont().getSize());
            fontLbl[index].setFont(modFont.getFont());
            gbc.weightx = 1.0;
            gbc.gridx++;
            innerFontPnl.add(fontLbl[index], gbc);
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            gbc.gridy++;
            index++;
        }

        // Set the scroll bar scroll increment
        fontScrollPane.getVerticalScrollBar().setUnitIncrement(fontBtn[0].getPreferredSize().height / 2
                                                               + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());

        // Calculate the maximum required height of the panel containing the font check boxes (= #
        // of rows * row height)
        maxScrollPaneHeight = Math.max(maxScrollPaneHeight,
                                       10 * fontScrollPane.getPreferredSize().height / fontBtn.length);
    }

    /**********************************************************************************************
     * Update a font
     *
     * @param modFont
     *            reference to the modifiable font's information
     *
     * @param family
     *            font family
     *
     * @param style
     *            font style
     *
     * @param size
     *            font size
     *
     * @param dialogs
     *            array of preference dialogs to update
     *********************************************************************************************/
    private void updateFont(ModifiableFontInfo modFont,
                            String family,
                            int style,
                            int size,
                            CcddDialogHandler[] dialogs)
    {
        // Update the modifiable font information to the new font
        modFont.setFont(family, style, size, ccddMain.getProgPrefs());

        // Check if this is a change to the tool tip font
        if (modFont == ModifiableFontInfo.TOOL_TIP)
        {
            // Update the tool tip text font. This is ignored by some look & feels (e.g. Nimbus and
            // GTK+)
            UIManager.getDefaults().put("ToolTip.font", ModifiableFontInfo.TOOL_TIP.getFont());
        }
        // Not a change to the tool tip font
        else
        {
            // Update the visible GUI components to the new font
            ccddMain.updateGUI(GUIUpdateType.FONT, dialogs);
        }
    }

    /**********************************************************************************************
     * Add the color update tab to the tabbed pane
     *********************************************************************************************/
    private void addColorTab()
    {
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create storage for the check boxes representing each modifiable color
        ColorCheckBox[] colorCbox = new ColorCheckBox[ModifiableColorInfo.values().length];

        // Create a panel to contain the color components
        JPanel innerColorPnl = new JPanel(new GridBagLayout());
        innerColorPnl.setBorder(emptyBorder);

        // Use an outer panel so that the components can be forced to the top of the tab area
        JPanel colorPnl = new JPanel(new BorderLayout());
        colorPnl.setBorder(emptyBorder);
        colorPnl.add(innerColorPnl, BorderLayout.PAGE_START);

        // Create a scroll pane in which to display the color selection check boxes
        JScrollPane colorScrollPane = new JScrollPane(colorPnl);
        colorScrollPane.setBorder(emptyBorder);
        colorScrollPane.setViewportBorder(emptyBorder);

        // Add the color selection tab to the tabbed pane
        tabbedPane.addTab(COLOR, null, colorScrollPane, "Change program colors");

        // Create a listener for the color check box selections
        ActionListener colorCboxListener = new ActionListener()
        {
            /**************************************************************************************
             * Handle a color check box selection
             *************************************************************************************/
            @Override
            public void actionPerformed(final ActionEvent ae)
            {
                // Get the reference to the modifiable color information based on the button name
                // (in which the modifiable color's program preferences key is stored)
                final ModifiableColorInfo modColor = ModifiableColorInfo.getModifiableColorInfo(((ColorCheckBox) ae.getSource()).getName());

                // Create a color chooser
                final JColorChooser chooser = new JColorChooser();

                // Set the initial color in the color chooser to the modifiable color's current
                // color
                chooser.setColor(modColor.getColor());

                // Create the color choice dialog
                final CcddDialogHandler dialog = new CcddDialogHandler();

                // Add a listener for the Update button
                ActionListener okayAction = new ActionListener()
                {
                    /******************************************************************************
                     * Handle an Update button press event
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent aes)
                    {
                        // Get the currently selected color from the color chooser
                        Color newColor = chooser.getColor();

                        // Check if the color changed
                        if (modColor.getColor().getRed() != newColor.getRed()
                            || modColor.getColor().getGreen() != newColor.getGreen()
                            || modColor.getColor().getBlue() != newColor.getBlue())
                        {
                            // Update the modifiable color information to the new color
                            updateColor(modColor,
                                        newColor.getRed(),
                                        newColor.getGreen(),
                                        newColor.getBlue(),
                                        new CcddDialogHandler[] {CcddPreferencesDialog.this,
                                                                 dialog},
                                        (ColorCheckBox) ae.getSource());
                        }
                    }
                };

                // Add a listener for the Default button
                ActionListener defaultAction = new ActionListener()
                {
                    /******************************************************************************
                     * Handle a Default button press event
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent aes)
                    {
                        // Check if the color changed
                        if (modColor.getColor().getRed() != modColor.getDefaultRed()
                            || modColor.getColor().getGreen() != modColor.getDefaultGreen()
                            || modColor.getColor().getBlue() != modColor.getDefaultBlue())
                        {
                            // Update the modifiable color information to its default color
                            updateColor(modColor,
                                        modColor.getDefaultRed(),
                                        modColor.getDefaultGreen(),
                                        modColor.getDefaultBlue(),
                                        new CcddDialogHandler[] {CcddPreferencesDialog.this,
                                                                 dialog},
                                        (ColorCheckBox) ae.getSource());

                            // Update the color chooser to the default color
                            chooser.setColor(modColor.getColor());
                        }
                    }
                };

                // Display the color selection dialog
                dialog.showOptionsDialog(CcddPreferencesDialog.this,
                                         dialog.getColorChoicePanel(chooser, modColor.getColor()),
                                         getButtonPanel(okayAction, defaultAction, dialog),
                                         btnClose,
                                         "Select Color: " + modColor.getName(),
                                         false);
            }
        };

        int index = 0;

        // Step through each modifiable color
        for (ModifiableColorInfo modColor : ModifiableColorInfo.values())
        {
            // Create and add a color selection check box
            colorCbox[index] = new ColorCheckBox(modColor.getName(), modColor.getColor());
            colorCbox[index].setName(modColor.getPreferenceKey());
            colorCbox[index].setToolTipText(CcddUtilities.wrapText(modColor.getDescription(),
                                                                   ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            colorCbox[index].addActionListener(colorCboxListener);
            innerColorPnl.add(colorCbox[index], gbc);
            gbc.weightx = 1.0;
            gbc.gridx++;
            innerColorPnl.add(new JLabel(""), gbc);
            gbc.insets.top = 0;
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            gbc.gridy++;
            index++;
        }

        // Set the scroll bar scroll increment
        colorScrollPane.getVerticalScrollBar().setUnitIncrement(colorCbox[0].getPreferredSize().height / 2
                                                                + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());

        // Calculate the maximum required height of the panel containing the color check boxes (= #
        // of rows * row height)
        maxScrollPaneHeight = Math.max(maxScrollPaneHeight,
                                       10 * colorScrollPane.getPreferredSize().height / colorCbox.length);
    }

    /**********************************************************************************************
     * Update a color
     *
     * @param modColor
     *            reference to the modifiable color's information
     *
     * @param red
     *            red component
     *
     * @param green
     *            green component
     *
     * @param blue
     *            blue component
     *
     * @param dialogs
     *            array of preference dialogs to update
     *
     * @param checkBox
     *            reference to the color check box
     *********************************************************************************************/
    private void updateColor(ModifiableColorInfo modColor,
                             int red,
                             int green,
                             int blue,
                             CcddDialogHandler[] dialogs,
                             ColorCheckBox checkBox)
    {
        // Update the modifiable color information to the new color
        modColor.setModifiableColor(red, green, blue, ccddMain.getProgPrefs());

        // Check if this is a change to the tool tip text color
        if (modColor == ModifiableColorInfo.TOOL_TIP_TEXT)
        {
            // Update the tool tip text color. This is ignored by some look & feels (e.g. Nimbus
            // and GTK+)
            UIManager.getDefaults().put("ToolTip.foreground", modColor.getColor());
        }
        // Check if this is a change to the tool tip background color
        else if (modColor == ModifiableColorInfo.TOOL_TIP_BACK)
        {
            // Update the tool tip background color. This is ignored by some look & feels (e.g.
            // Nimbus and GTK+)
            UIManager.getDefaults().put("ToolTip.background", modColor.getColor());
        }
        // Not a tool tip color change
        else
        {
            // Update the visible GUI components to the new font
            ccddMain.updateGUI(GUIUpdateType.COLOR, dialogs);
        }

        // Update the color selection check box color
        checkBox.setIconColor(modColor.getColor());
    }

    /**********************************************************************************************
     * Add the size update tab to the tabbed pane
     *********************************************************************************************/
    private void addSizeTab()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

        // Create storage for the description and input field representing each modifiable size
        JLabel[] sizeLbl = new JLabel[ModifiableSizeInfo.values().length];
        JButton[] sizeBtn = new JButton[ModifiableSizeInfo.values().length];
        sizeFld = new JTextField[ModifiableSizeInfo.values().length];

        // Create a panel to contain the size components
        JPanel innerSizePnl = new JPanel(new GridBagLayout());
        innerSizePnl.setBorder(emptyBorder);

        // Use an outer panel so that the components can be forced to the top of the tab area
        JPanel sizePnl = new JPanel(new BorderLayout());
        sizePnl.setBorder(emptyBorder);
        sizePnl.add(innerSizePnl, BorderLayout.PAGE_START);

        // Create a scroll pane in which to display the size labels and fields
        JScrollPane sizeScrollPane = new JScrollPane(sizePnl);
        sizeScrollPane.setBorder(emptyBorder);
        sizeScrollPane.setViewportBorder(emptyBorder);

        // Add the size update tab to the tabbed pane
        tabbedPane.addTab(SIZE, null, sizeScrollPane, "Change program maximum width and length values");

        // Create a listener for the default size buttons
        ActionListener defaultListener = new ActionListener()
        {
            /**************************************************************************************
             * Update the size to the default value
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the index of the size field array, which is stored as the button's name
                int index = Integer.valueOf(((JButton) ae.getSource()).getName());

                // Set the size to its default value
                sizeFld[index].setText(String.valueOf(ModifiableSizeInfo.values()[index].getDefault()));
            }
        };

        int index = 0;

        // Step through each modifiable size
        for (final ModifiableSizeInfo modSize : ModifiableSizeInfo.values())
        {
            // Create the size label and input field
            sizeLbl[index] = new JLabel(modSize.getName()
                                        + " ("
                                        + modSize.getMinimum()
                                        + ", "
                                        + modSize.getMaximum()
                                        + ")");
            sizeLbl[index].setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            sizeLbl[index].setToolTipText(CcddUtilities.wrapText(modSize.getDescription(),
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            innerSizePnl.add(sizeLbl[index], gbc);
            sizeFld[index] = new JTextField(String.valueOf(modSize.getSize()), 3);
            sizeFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            sizeFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            sizeFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            sizeFld[index].setBorder(border);
            sizeFld[index].setName(modSize.getPreferenceKey());
            sizeFld[index].setToolTipText(CcddUtilities.wrapText(modSize.getDescription(),
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

            // Create an input verifier to keep the size value within its specified limits
            sizeFld[index].setInputVerifier(new InputVerifier()
            {
                // Storage for the last valid value entered; used to restore the size value if an
                // invalid value is entered
                String lastValid = String.valueOf(modSize.getSize());

                /**********************************************************************************
                 * Verify the contents of a the size field
                 *********************************************************************************/
                @Override
                public boolean verify(JComponent input)
                {
                    boolean isValid = true;

                    JTextField sizeFld = (JTextField) input;

                    try
                    {
                        // Get the reference to the modifiable size information using its program
                        // preferences key, which is stored as the field's name
                        ModifiableSizeInfo modSize = ModifiableSizeInfo.getModifiableSizeInfo(input.getName());

                        // Remove any leading or trailing white space characters
                        String size = sizeFld.getText().trim();

                        // Check if the size field is empty
                        if (size.isEmpty())
                        {
                            throw new CCDDException(modSize.getName() + "<b>' cannot be blank");
                        }

                        // Check if the size value isn't a positive integer
                        if (!size.matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
                        {
                            throw new CCDDException(modSize.getName()
                                                    + "<b>' must be a positive integer");
                        }

                        // Convert the text to an integer
                        int currentValue = Integer.valueOf(size);

                        // Check if the size value is outside of its specified limits
                        if (currentValue < modSize.getMinimum()
                            || currentValue > modSize.getMaximum())
                        {
                            throw new CCDDException(modSize.getName()
                                                    + "<b>' is outside allowable limits");
                        }

                        // Update the size field to the new (valid) value
                        sizeFld.setText(size);

                        // Store the new value as the last valid value
                        lastValid = sizeFld.getText();
                    }
                    catch (CCDDException ce)
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddPreferencesDialog.this,
                                                                  "<html><b>The value for '</b>"
                                                                                              + ce.getMessage(),
                                                                  "Missing/Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);

                        // Restore the size field to the last valid value
                        sizeFld.setText(lastValid);

                        // Set the flag to indicate the size value is invalid
                        isValid = false;

                        // Toggle the controls enable status so that the buttons are redrawn
                        // correctly
                        CcddPreferencesDialog.this.setControlsEnabled(false);
                        CcddPreferencesDialog.this.setControlsEnabled(true);
                    }

                    return isValid;
                }
            });

            // Add the size field to the size panel
            gbc.gridx++;
            innerSizePnl.add(sizeFld[index], gbc);

            // Create a button for setting the size to its default value and add it to the size
            // panel
            sizeBtn[index] = new JButton("Default (" + modSize.getDefault() + ")");
            sizeBtn[index].setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
            sizeBtn[index].setName(String.valueOf(index));
            sizeBtn[index].addActionListener(defaultListener);
            gbc.gridx++;
            innerSizePnl.add(sizeBtn[index], gbc);
            gbc.weightx = 1.0;
            gbc.gridx++;
            innerSizePnl.add(new JLabel(""), gbc);
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            gbc.gridy++;
            index++;
        }

        // Set the scroll bar scroll increment
        sizeScrollPane.getVerticalScrollBar().setUnitIncrement(sizeFld[0].getPreferredSize().height / 2
                                                               + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());

        // Calculate the maximum required height of the panel containing the size labels and fields
        // (= # of rows * row height)
        maxScrollPaneHeight = Math.max(maxScrollPaneHeight,
                                       10 * sizeScrollPane.getPreferredSize().height / sizeFld.length);
    }

    /**********************************************************************************************
     * Add the spacing update tab to the tabbed pane
     *********************************************************************************************/
    private void addSpacingTab()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

        // Create storage for the description and input field representing each modifiable spacing
        JLabel[] spacingLbl = new JLabel[ModifiableSpacingInfo.values().length];
        JButton[] spacingBtn = new JButton[ModifiableSpacingInfo.values().length];
        spacingFld = new JTextField[ModifiableSpacingInfo.values().length];

        // Create a panel to contain the spacing components
        JPanel innerSpacingPnl = new JPanel(new GridBagLayout());
        innerSpacingPnl.setBorder(emptyBorder);

        // Use an outer panel so that the components can be forced to the top of the tab area
        JPanel spacingPnl = new JPanel(new BorderLayout());
        spacingPnl.setBorder(emptyBorder);
        spacingPnl.add(innerSpacingPnl, BorderLayout.PAGE_START);

        // Create a scroll pane in which to display the spacing labels and fields
        JScrollPane spacingScrollPane = new JScrollPane(spacingPnl);
        spacingScrollPane.setBorder(emptyBorder);
        spacingScrollPane.setViewportBorder(emptyBorder);

        // Add the note to the panel
        JLabel noteLbl = new JLabel("<html><i>Note: Open windows must be closed and "
                                    + "reopened for spacing changes to take effect");
        noteLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        noteLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        noteLbl.setBorder(BorderFactory.createEmptyBorder(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2,
                                                          ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                          ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                          ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()));

        // Create a panel to contain the scroll pane and a note with fixed position at the bottom
        // of the panel
        JPanel scrollAndNotePnl = new JPanel(new BorderLayout());
        scrollAndNotePnl.add(spacingScrollPane, BorderLayout.CENTER);
        scrollAndNotePnl.add(noteLbl, BorderLayout.PAGE_END);

        // Add the spacing update tab to the tabbed pane
        tabbedPane.addTab(SPACING, null, scrollAndNotePnl, "Change program spacing values");

        // Create a listener for the default spacing buttons
        ActionListener defaultListener = new ActionListener()
        {
            /**************************************************************************************
             * Update the spacing to the default value
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the index of the spacing field array, which is stored as the button's name
                int index = Integer.valueOf(((JButton) ae.getSource()).getName());

                // Set the spacing to its default value
                spacingFld[index].setText(String.valueOf(ModifiableSpacingInfo.values()[index].getDefault()));
            }
        };

        int index = 0;

        // Step through each modifiable spacing
        for (final ModifiableSpacingInfo modSpacing : ModifiableSpacingInfo.values())
        {
            // Create the spacing label and input field
            spacingLbl[index] = new JLabel(modSpacing.getName()
                                           + " ("
                                           + modSpacing.getMinimum()
                                           + ", "
                                           + modSpacing.getMaximum()
                                           + ")");
            spacingLbl[index].setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            spacingLbl[index].setToolTipText(CcddUtilities.wrapText(modSpacing.getDescription(),
                                                                    ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            innerSpacingPnl.add(spacingLbl[index], gbc);
            spacingFld[index] = new JTextField(String.valueOf(modSpacing.getSpacing()), 3);
            spacingFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            spacingFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            spacingFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            spacingFld[index].setBorder(border);
            spacingFld[index].setName(modSpacing.getPreferenceKey());
            spacingFld[index].setToolTipText(CcddUtilities.wrapText(modSpacing.getDescription(),
                                                                    ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

            // Create an input verifier to keep the spacing value within its specified limits
            spacingFld[index].setInputVerifier(new InputVerifier()
            {
                // Storage for the last valid value entered; used to restore the spacing value if
                // an invalid value is entered
                String lastValid = String.valueOf(modSpacing.getSpacing());

                /**********************************************************************************
                 * Verify the contents of a the spacing field
                 *********************************************************************************/
                @Override
                public boolean verify(JComponent input)
                {
                    boolean isValid = true;

                    JTextField spacingFld = (JTextField) input;

                    try
                    {
                        // Get the reference to the modifiable spacing information using its
                        // program preferences key, which is stored as the field's name
                        ModifiableSpacingInfo modSpacing = ModifiableSpacingInfo.getModifiableSpacingInfo(input.getName());

                        // Remove any leading or trailing white space characters
                        String spacing = spacingFld.getText().trim();

                        // Check if the spacing field is empty
                        if (spacing.isEmpty())
                        {
                            throw new CCDDException(modSpacing.getName() + "<b>' cannot be blank");
                        }

                        // Check if the spacing value isn't a positive integer
                        if (!spacing.matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
                        {
                            throw new CCDDException(modSpacing.getName()
                                                    + "<b>' must be a positive integer");
                        }

                        // Convert the text to an integer
                        int currentValue = Integer.valueOf(spacing);

                        // Check if the spacing value is outside of its specified limits
                        if (currentValue < modSpacing.getMinimum()
                            || currentValue > modSpacing.getMaximum())
                        {
                            throw new CCDDException(modSpacing.getName()
                                                    + "<b>' is outside allowable limits");
                        }

                        // Update the spacing field to the new (valid) value
                        spacingFld.setText(spacing);

                        // Store the new value as the last valid value
                        lastValid = spacingFld.getText();
                    }
                    catch (CCDDException ce)
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddPreferencesDialog.this,
                                                                  "<html><b>The value for '</b>"
                                                                                              + ce.getMessage(),
                                                                  "Missing/Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);

                        // Restore the spacing field to the last valid value
                        spacingFld.setText(lastValid);

                        // Set the flag to indicate the spacing value is invalid
                        isValid = false;

                        // Toggle the controls enable status so that the buttons are redrawn
                        // correctly
                        CcddPreferencesDialog.this.setControlsEnabled(false);
                        CcddPreferencesDialog.this.setControlsEnabled(true);
                    }

                    return isValid;
                }
            });

            // Add the spacing field to the spacing panel
            gbc.gridx++;
            innerSpacingPnl.add(spacingFld[index], gbc);

            // Create a button for setting the spacing to its default value and add it to the
            // spacing panel
            spacingBtn[index] = new JButton("Default (" + modSpacing.getDefault() + ")");
            spacingBtn[index].setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
            spacingBtn[index].setName(String.valueOf(index));
            spacingBtn[index].addActionListener(defaultListener);
            gbc.gridx++;
            innerSpacingPnl.add(spacingBtn[index], gbc);
            gbc.weightx = 1.0;
            gbc.gridx++;
            innerSpacingPnl.add(new JLabel(""), gbc);
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            gbc.gridy++;
            index++;
        }

        // Set the scroll bar scroll increment
        spacingScrollPane.getVerticalScrollBar().setUnitIncrement(spacingFld[0].getPreferredSize().height / 2
                                                                  + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());

        // Calculate the maximum required height of the panel containing the spacing labels and
        // fields (= # of rows * row height)
        maxScrollPaneHeight = Math.max(maxScrollPaneHeight,
                                       10 * spacingScrollPane.getPreferredSize().height / spacingFld.length);
    }

    /**********************************************************************************************
     * Add the path update tab to the tabbed pane
     *********************************************************************************************/
    private void addPathTab()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

        // Create storage for the description and input field representing each modifiable path
        JLabel[] pathLbl = new JLabel[ModifiablePathInfo.values().length];
        JButton[] pathBtn = new JButton[ModifiablePathInfo.values().length];
        pathFld = new JTextField[ModifiablePathInfo.values().length];

        // Create a panel to contain the path components
        JPanel innerPathPnl = new JPanel(new GridBagLayout());
        innerPathPnl.setBorder(emptyBorder);

        // Use an outer panel so that the components can be forced to the top of the tab area
        JPanel pathPnl = new JPanel(new BorderLayout());
        pathPnl.setBorder(emptyBorder);
        pathPnl.add(innerPathPnl, BorderLayout.PAGE_START);

        // Create a scroll pane in which to display the path labels and fields
        JScrollPane pathScrollPane = new JScrollPane(pathPnl);
        pathScrollPane.setBorder(emptyBorder);
        pathScrollPane.setViewportBorder(emptyBorder);

        // Add the path update tab to the tabbed pane
        tabbedPane.addTab(PATH, null, pathScrollPane, "Change program paths");

        // Create a listener for the path selection buttons
        ActionListener defaultListener = new ActionListener()
        {
            /**************************************************************************************
             * Update the path to the selection
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the index of the path field array, which is stored as the button's name
                int index = Integer.valueOf(((JButton) ae.getSource()).getName());

                // Allow the user to select the script file path + name
                FileEnvVar[] pathFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                               CcddPreferencesDialog.this,
                                                                               "Select Path",
                                                                               pathFld[index].getText(),
                                                                               DialogOption.OK_CANCEL_OPTION);

                // Check if a path is selected
                if (pathFile != null && pathFile[0] != null)
                {
                    // Display the file name in the path name field
                    pathFld[index].setText(pathFile[0].getAbsolutePathWithEnvVars());
                }
            }
        };

        int index = 0;

        // Step through each modifiable path
        for (final ModifiablePathInfo modPath : ModifiablePathInfo.values())
        {
            // Create the path label and input field
            pathLbl[index] = new JLabel(modPath.getName());
            pathLbl[index].setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            pathLbl[index].setToolTipText(CcddUtilities.wrapText(modPath.getDescription(),
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            innerPathPnl.add(pathLbl[index], gbc);
            pathFld[index] = new JTextField(String.valueOf(modPath.getPath()), 40);
            pathFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            pathFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            pathFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            pathFld[index].setBorder(border);
            pathFld[index].setName(modPath.getPreferenceKey());
            pathFld[index].setToolTipText(CcddUtilities.wrapText(modPath.getDescription(),
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

            // Add the path field to the path panel
            gbc.gridx++;
            innerPathPnl.add(pathFld[index], gbc);
            pathBtn[index] = new JButton("Select...");
            pathBtn[index].setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
            pathBtn[index].setName(String.valueOf(index));
            pathBtn[index].addActionListener(defaultListener);
            gbc.insets.right = 0;
            gbc.gridx++;
            innerPathPnl.add(pathBtn[index], gbc);
            gbc.insets.left = 0;
            gbc.weightx = 1.0;
            gbc.gridx++;
            innerPathPnl.add(new JLabel(""), gbc);
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            gbc.gridy++;
            index++;
        }

        // Set the scroll bar scroll increment
        pathScrollPane.getVerticalScrollBar().setUnitIncrement(pathFld[0].getPreferredSize().height / 2
                                                               + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());

        // Calculate the maximum required height of the panel containing the path labels and fields
        // (= # of rows * row height)
        maxScrollPaneHeight = Math.max(maxScrollPaneHeight,
                                       10 * pathScrollPane.getPreferredSize().height / pathFld.length);
    }

    /**********************************************************************************************
     * Add the other settings update tab to the tabbed pane
     *********************************************************************************************/
    private void addOtherTab()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

        // Create storage for the description and input field representing each modifiable other
        // setting
        JLabel[] otherLbl = new JLabel[ModifiableOtherSettingInfo.values().length];
        JButton[] otherBtn = new JButton[ModifiableOtherSettingInfo.values().length];
        otherFld = new JTextField[ModifiableOtherSettingInfo.values().length];

        // Create a panel to contain the other setting components
        JPanel innerOtherPnl = new JPanel(new GridBagLayout());
        innerOtherPnl.setBorder(emptyBorder);

        // Use an outer panel so that the components can be forced to the top of the tab area
        JPanel otherPnl = new JPanel(new BorderLayout());
        otherPnl.setBorder(emptyBorder);
        otherPnl.add(innerOtherPnl, BorderLayout.PAGE_START);

        // Create a scroll pane in which to display the other setting labels and fields
        JScrollPane otherScrollPane = new JScrollPane(otherPnl);
        otherScrollPane.setBorder(emptyBorder);
        otherScrollPane.setViewportBorder(emptyBorder);

        // Add the other setting update tab to the tabbed pane
        tabbedPane.addTab(OTHER, null, otherScrollPane, "Change other program settings");

        // Create a listener for the default other setting buttons
        ActionListener defaultListener = new ActionListener()
        {
            /**************************************************************************************
             * Update the other setting to the default value
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the index of the other setting field array, which is stored as the button's
                // name
                int index = Integer.valueOf(((JButton) ae.getSource()).getName());

                // Set the other setting to its default value
                otherFld[index].setText(String.valueOf(ModifiableOtherSettingInfo.values()[index].getDefault()));
            }
        };

        int index = 0;

        // Step through each modifiable other setting
        for (final ModifiableOtherSettingInfo modOther : ModifiableOtherSettingInfo.values())
        {
            // Create the other setting label and input field
            otherLbl[index] = new JLabel(modOther.getName());
            otherLbl[index].setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            otherLbl[index].setToolTipText(CcddUtilities.wrapText(modOther.getDescription(),
                                                                  ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            innerOtherPnl.add(otherLbl[index], gbc);
            otherFld[index] = new JTextField(modOther.getValue(), 40);
            otherFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            otherFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            otherFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            otherFld[index].setBorder(border);
            otherFld[index].setName(modOther.getPreferenceKey());
            otherFld[index].setToolTipText(CcddUtilities.wrapText(modOther.getDescription(),
                                                                  ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

            // Add the other setting field to the other panel
            gbc.gridx++;
            innerOtherPnl.add(otherFld[index], gbc);

            // Create a button for setting the other setting to its default value and add it to the
            // other panel
            otherBtn[index] = new JButton("Default");
            otherBtn[index].setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
            otherBtn[index].setName(String.valueOf(index));
            otherBtn[index].addActionListener(defaultListener);
            gbc.gridx++;
            innerOtherPnl.add(otherBtn[index], gbc);
            gbc.weightx = 1.0;
            gbc.gridx++;
            innerOtherPnl.add(new JLabel(""), gbc);
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            gbc.gridy++;
            index++;
        }

        // Set the scroll bar scroll increment
        otherScrollPane.getVerticalScrollBar().setUnitIncrement(otherFld[0].getPreferredSize().height / 2
                                                                + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());

        // Calculate the maximum required height of the panel containing the other setting labels
        // and fields (= # of rows * row height)
        maxScrollPaneHeight = Math.max(maxScrollPaneHeight,
                                       10 * otherScrollPane.getPreferredSize().height / otherFld.length);
    }

    /**********************************************************************************************
     * Create the button panel for the font/color chooser dialogs containing the Okay, Default, and
     * Close buttons
     *
     * @param okayAction
     *            reference to the action listener to handle Okay button events
     *
     * @param defaultAction
     *            reference to the action listener to handle Default button events
     *
     * @param dialog
     *            reference to the dialog calling this method
     *
     * @return Reference to the font/color chooser button panel
     *********************************************************************************************/
    private JPanel getButtonPanel(ActionListener okayAction,
                                  ActionListener defaultAction,
                                  final CcddDialogHandler dialog)
    {
        JPanel buttonPnl = new JPanel();

        // Update button
        JButton btnUpdate = CcddButtonPanelHandler.createButton("Update",
                                                                STORE_ICON,
                                                                KeyEvent.VK_U,
                                                                "Update the GUI components using the selected values");

        // Add a listener for the Okay button
        btnUpdate.addActionListener(okayAction);

        // Default button
        JButton btnDefault = CcddButtonPanelHandler.createButton("Default",
                                                                 UNDO_ICON,
                                                                 KeyEvent.VK_D,
                                                                 "Update the GUI components using the default values");

        // Add a listener for the Default button
        btnDefault.addActionListener(defaultAction);

        // Close button
        btnClose = CcddButtonPanelHandler.createButton("Close",
                                                       CLOSE_ICON,
                                                       KeyEvent.VK_C,
                                                       "Close the selection dialog");

        // Create a listener for the Close button
        btnClose.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Close the selection dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                dialog.closeDialog();
            }
        });

        // Add buttons in the order in which they'll appear (left to right)
        buttonPnl.add(btnUpdate);
        buttonPnl.add(btnDefault);
        buttonPnl.add(btnClose);

        return buttonPnl;
    }
}
