/**
 * CFS Command & Data Dictionary macro handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.MACRO_IDENTIFIER;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import CCDD.CcddClasses.PaddedComboBox;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.SearchType;

/******************************************************************************
 * CFS Command & Data Dictionary macro handler class
 *****************************************************************************/
public class CcddMacroHandler
{
    // Class reference
    private CcddDbCommandHandler dbCommand;

    // Pop-up combo box for displaying the macro names and the dialog to
    // contain it
    private PaddedComboBox macroCbox;
    private JDialog comboDlg;

    // List containing the macro names and associated values
    private List<String[]> macros;

    // Macro name pattern
    private final Pattern macroPattern;

    /**************************************************************************
     * Macro location class
     *************************************************************************/
    class MacroLocation
    {
        private final String macroName;
        private final int start;

        /**********************************************************************
         * Macro location class constructor
         * 
         * @param macroName
         *            macro name, including the macro delimiters
         * 
         * @param start
         *            index of the beginning of the macro name in the text
         *            string
         *********************************************************************/
        MacroLocation(String macroName, int start)
        {
            this.macroName = macroName;
            this.start = start;
        }

        /**********************************************************************
         * Get the macro name, including the macro delimiters
         * 
         * @return Macro name, including the macro delimiters
         *********************************************************************/
        protected String getMacroName()
        {
            return macroName;
        }

        /**********************************************************************
         * Get the index of the beginning of the macro name in the text string
         * 
         * @return Index of the beginning of the macro name in the text string
         *********************************************************************/
        protected int getStart()
        {
            return start;
        }
    }

    /**************************************************************************
     * Macro handler class constructor used when setting the macros from a
     * source other than those in the project database
     * 
     * @param macros
     *            list of string arrays containing macro names and the
     *            corresponding macro values
     *************************************************************************/
    CcddMacroHandler(List<String[]> macros)
    {
        this.macros = macros;

        // Create the macro name search pattern
        macroPattern = Pattern.compile("^.*?("
                                       + MACRO_IDENTIFIER
                                       + "([^"
                                       + MACRO_IDENTIFIER
                                       + "]+)"
                                       + MACRO_IDENTIFIER
                                       + ").*$",
                                       Pattern.CASE_INSENSITIVE);
    }

    /**************************************************************************
     * Macro handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddMacroHandler(CcddMain ccddMain)
    {
        // Load the macro table from the project database
        this(ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.MACROS,
                                                                          true,
                                                                          ccddMain.getMainFrame()));
        dbCommand = ccddMain.getDbCommandHandler();
    }

    /**************************************************************************
     * Get the macro data
     * 
     * @return List of string arrays containing macro names and the
     *         corresponding macro values
     *************************************************************************/
    protected List<String[]> getMacroData()
    {
        return macros;
    }

    /**************************************************************************
     * Set the macro data to the supplied array
     * 
     * @param macros
     *            list of string arrays containing macro names and the
     *            corresponding macro values
     *************************************************************************/
    protected void setMacroData(List<String[]> macros)
    {
        this.macros = new ArrayList<String[]>(macros);
    }

    /**************************************************************************
     * Get the macro name encased in the macro identifier character(s)
     * 
     * @param macroName
     *            macro name
     * 
     * @return Macro name encased in the macro identifier character(s)
     *************************************************************************/
    protected String getFullMacroName(String macroName)
    {
        return MACRO_IDENTIFIER + macroName + MACRO_IDENTIFIER;
    }

    /**************************************************************************
     * Get a list of all macro name locations in the specified text string
     * 
     * @param text
     *            text string to search for macro names
     * 
     * @return List of all macro name locations in the specified text string
     *************************************************************************/
    private List<MacroLocation> getMacroLocation(String text)
    {
        Matcher matcher;
        int start = 0;

        // Create storage for the macro name locations
        List<MacroLocation> locations = new ArrayList<MacroLocation>();

        do
        {
            // Locate the macro name pattern within the text string
            matcher = macroPattern.matcher(text);

            // Check if the macro name pattern is present
            if (matcher.matches())
            {
                // Get the macro name, with the macro delimiters
                String macroName = matcher.group(1);

                // Store the index of the macro name pattern
                int position = text.indexOf(macroName);

                // Check if the pattern matches a defined macro. This uses the
                // macro name without the delimiters
                if (isMacroExists(matcher.group(2)))
                {
                    // Update the macro name starting position and remove the
                    // characters from the beginning of the text string to the
                    // end of the macro name
                    start += position;
                    text = text.substring(position + macroName.length());

                    // Store the location for this macro
                    locations.add(new MacroLocation(macroName, start));

                    // Update the start index to skip the macro name
                    start += macroName.length();
                }
                // Looks like a macro but doesn't match a defined name
                else
                {
                    // Remove the first character in the text, advance the
                    // starting position by 1, and try matching again
                    text = text.substring(position + 1);
                    start += position + 1;
                }
            }
        } while (matcher.matches());
        // Process the text string until no macro names are found

        return locations;
    }

    /**************************************************************************
     * Replace all instances of the specified the macro name in the supplied
     * text string
     * 
     * @param oldName
     *            original macro name, including the delimiters
     * 
     * @param newName
     *            new macro name, including the delimiters
     * 
     * @param text
     *            text string in which to replace the macro name
     *************************************************************************/
    protected String replaceMacroName(String oldName,
                                      String newName,
                                      String text)
    {
        // Get the locations of the macro(s) in the supplied string
        List<MacroLocation> locations = getMacroLocation(text);

        // Step through each macro in the text string. This is done in reverse
        // since the start indices after the current location change as the
        // text string length changes
        for (int index = locations.size() - 1; index >= 0; index--)
        {
            // Check if this location contains the target macro name
            if (text.startsWith(oldName, locations.get(index).getStart()))
            {
                // Replace the original macro name with the new one
                text = text.substring(0, locations.get(index).getStart())
                       + newName
                       + text.substring(locations.get(index).getStart()
                                        + oldName.length());
            }
        }

        return text;
    }

    /**************************************************************************
     * Display a pop-up combo box containing the names of the defined macros.
     * When the user selects a macro insert it into the supplied text component
     * 
     * @param textComp
     *            text component over which to display the pop-up combo box and
     *            insert the selected macro name
     * 
     * @param inputType
     *            input data type of the text component
     *************************************************************************/
    protected void insertMacroName(final JTextComponent textComp,
                                   InputDataType inputType)
    {
        comboDlg = new JDialog();

        // Check if any macros exist
        if (!macros.isEmpty())
        {
            List<String> validMacros = new ArrayList<String>();
            List<String> toolTips = new ArrayList<String>();

            // Step through each macro
            for (String[] macro : macros)
            {
                // Get the text component's text with the macro value replacing
                // the macro name
                String text = getInsertedMacro(macro[MacrosColumn.VALUE.ordinal()],
                                               textComp);

                // Check if the text component's text, with the macro's value
                // inserted, is allowed in the target text component based on
                // the component's input type
                if (text.isEmpty() || text.matches(inputType.getInputMatch()))
                {
                    // Add the macro name to the list with its value as the
                    // item's tool tip text
                    validMacros.add(macro[MacrosColumn.MACRO_NAME.ordinal()]);
                    toolTips.add(macro[MacrosColumn.VALUE.ordinal()]);
                }
            }

            // Check if any of the macro's are applicable to the target text
            // component
            if (!validMacros.isEmpty())
            {
                // Create the pop-up combo box
                macroCbox = new PaddedComboBox(validMacros.toArray(new String[0]),
                                               toolTips.toArray(new String[0]),
                                               ModifiableFontInfo.DATA_TABLE_CELL.getFont());
                macroCbox.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

                // Set the first macro as initially selected
                macroCbox.setSelectedIndex(0);

                // Set the property to allow the arrow keys to be used to
                // change the macro selection in the combo box
                macroCbox.putClientProperty("JComboBox.isTableCellEditor",
                                            Boolean.TRUE);

                // Add a listener for selection events in the macro pop-up
                // combo box
                macroCbox.addActionListener(new ActionListener()
                {
                    /**********************************************************
                     * Handle a selection event in the macro combo box
                     *********************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Get the selected macro's name and enclose it in the
                        // macro identifier character(s)
                        String macroName = getFullMacroName(((JComboBox<?>) ae.getSource()).getSelectedItem().toString().trim());

                        // Get the starting index of the selected text in the
                        // component
                        int start = textComp.getSelectionStart();

                        // Insert the macro into the text component's existing
                        // text, overwriting any of the text that is
                        // highlighted
                        textComp.setText(getInsertedMacro(macroName, textComp));
                        textComp.setSelectionStart(start);

                        // Select the macro name that was inserted
                        textComp.setSelectionEnd(start + macroName.length());

                        // Remove the macro pop-up and return to the caller.
                        // Get the selected macro's name and enclose it in the
                        // macro identifier character(s)
                        exitMacroCombo();
                    }
                });

                // Add a listener for key press events in the macro pop-up
                // combo box
                macroCbox.addKeyListener(new KeyAdapter()
                {
                    /**********************************************************
                     * Handle a key press event in the macro combo box
                     *********************************************************/
                    @Override
                    public void keyPressed(KeyEvent ke)
                    {
                        // Check if the escape key is pressed
                        if (ke.getKeyCode() == KeyEvent.VK_ESCAPE)
                        {
                            // Remove the macro pop-up and return to the caller
                            exitMacroCombo();
                        }
                    }
                });

                // Add a listener for changes to the expansion/contraction of
                // the combo box
                macroCbox.addPopupMenuListener(new PopupMenuListener()
                {
                    /**********************************************************
                     * Handle a combo box expansion event
                     *********************************************************/
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent pme)
                    {
                    }

                    /**********************************************************
                     * Handle a combo box contraction event
                     *********************************************************/
                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent pme)
                    {
                    }

                    /**********************************************************
                     * Handle a combo box cancel event. This occurs if the
                     * mouse is clicked outside the combo box
                     *********************************************************/
                    @Override
                    public void popupMenuCanceled(PopupMenuEvent pme)
                    {
                        // Remove the macro pop-up and return to the caller
                        exitMacroCombo();
                    }
                });

                // Create the dialog to contain the macro pop-up combo box. Set
                // to modeless so that pop-up dialog focus changes can be
                // detected
                comboDlg.setModalityType(ModalityType.MODELESS);
                comboDlg.setUndecorated(true);
                comboDlg.add(macroCbox, BorderLayout.NORTH);
                comboDlg.setSize(new Dimension(macroCbox.getPreferredSize()));

                // Add a listener for focus changes to the pop-up dialog
                comboDlg.addWindowFocusListener(new WindowFocusListener()
                {
                    /**********************************************************
                     * Handle a gain of pop-up dialog focus
                     *********************************************************/
                    @Override
                    public void windowGainedFocus(WindowEvent we)
                    {
                        // Create a runnable object to be executed
                        SwingUtilities.invokeLater(new Runnable()
                        {
                            /**************************************************
                             * Delay showing the pop-up; if this isn't done
                             * then the pop-up isn't expanded consistently
                             *************************************************/
                            @Override
                            public void run()
                            {
                                // Expand the combo box when it appears
                                macroCbox.showPopup();
                            }
                        });
                    }

                    /**********************************************************
                     * Handle a loss of pop-up dialog focus
                     *********************************************************/
                    @Override
                    public void windowLostFocus(WindowEvent we)
                    {
                        // Remove the macro pop-up and return to the caller
                        exitMacroCombo();
                    }
                });

                // Position and display the pop-up
                positionMacroPopup(textComp);
                comboDlg.setVisible(true);
            }
        }
    }

    /**************************************************************************
     * Position the dialog containing the macro pop-up combo box at the text
     * cursor position in the text component
     * 
     * @param textComp
     *            text component over which to display the pop-up combo box
     *************************************************************************/
    private void positionMacroPopup(JTextComponent textComp)
    {
        try
        {
            // Get the position of the text cursor within the text component
            Rectangle popUp = textComp.modelToView(textComp.getCaretPosition());

            // Position the pop-up at the text cursor position
            comboDlg.setLocation(textComp.getLocationOnScreen().x + popUp.x,
                                 textComp.getLocationOnScreen().y);
        }
        catch (BadLocationException ble)
        {
            // Position the pop-up at the left end of the text component
            comboDlg.setLocation(textComp.getLocationOnScreen().x,
                                 textComp.getLocationOnScreen().y);
        }
    }

    /**************************************************************************
     * Get the text of the specified text component with the macro name or
     * value inserted at the current selection point
     * 
     * @param text
     *            macro name or value
     * 
     * @param textComp
     *            text component over which the pop-up combo box is displayed
     * 
     * @return Text of the specified text component with the macro name or
     *         value inserted at the current selection point
     *************************************************************************/
    private String getInsertedMacro(String text, JTextComponent textComp)
    {
        return textComp.getText().substring(0, textComp.getSelectionStart())
               + text
               + textComp.getText().substring(textComp.getSelectionEnd());
    }

    /**************************************************************************
     * Remove the macro pop-up combo box and return to the caller
     *************************************************************************/
    private void exitMacroCombo()
    {
        comboDlg.setVisible(false);
        comboDlg.dispose();
    }

    /**************************************************************************
     * Get the value associated with the specified macro name
     * 
     * @param macroName
     *            macro name
     * 
     * @return Value associated with the specified macro name; returns null if
     *         the macro doesn't exist
     *************************************************************************/
    protected String getMacroValue(String macroName)
    {
        String macroValue = null;

        // Step through each defined macro
        for (String[] macro : macros)
        {
            // Check if the supplied name matches this macro's name
            if (macroName.equalsIgnoreCase(macro[MacrosColumn.MACRO_NAME.ordinal()]))
            {
                // Get the associated macro value and stop searching
                macroValue = macro[MacrosColumn.VALUE.ordinal()];
                break;
            }
        }

        return macroValue;
    }

    /**************************************************************************
     * Get the OID value associated with the specified macro name
     * 
     * @param macroName
     *            macro name
     * 
     * @return OID value associated with the specified macro name; returns null
     *         if the macro doesn't exist
     *************************************************************************/
    protected String getMacroIndex(String macroName)
    {
        String macroIndex = null;

        // Step through each defined macro
        for (String[] macro : macros)
        {
            // Check if the supplied name matches this macro's name
            if (macroName.equalsIgnoreCase(macro[MacrosColumn.MACRO_NAME.ordinal()]))
            {
                // Get the associated macro OID and stop searching
                macroIndex = macro[MacrosColumn.OID.ordinal()];
                break;
            }
        }

        return macroIndex;
    }

    /**************************************************************************
     * Check if the supplied text contains any macro references
     * 
     * @param text
     *            text string containing macro names
     * 
     * @return true if the text contains a macro reference
     *************************************************************************/
    protected boolean hasMacro(String text)
    {
        return text != null && text.matches(".*"
                                            + MACRO_IDENTIFIER
                                            + ".+"
                                            + MACRO_IDENTIFIER
                                            + ".*");
    }

    /**************************************************************************
     * Check if the supplied macro name is already in use (case insensitive)
     * 
     * @param macroName
     *            macro name
     * 
     * @return true if the supplied macro name is already in use
     *************************************************************************/
    protected boolean isMacroExists(String macroName)
    {
        boolean isExists = false;

        // Step through each defined macro
        for (String[] macro : macros)
        {
            // Check if the macro name matches the supplied name
            if (macroName.equalsIgnoreCase(macro[MacrosColumn.MACRO_NAME.ordinal()]))
            {
                // Set the flag to indicate the macro name already exists and
                // stop searching
                isExists = true;
                break;
            }
        }

        return isExists;
    }

    /**************************************************************************
     * Replace any macro names embedded in the supplied text with the
     * associated macro values
     * 
     * @param text
     *            text string containing macro names
     * 
     * @return Text string with any embedded macro names replaced with the
     *         associated macro values
     *************************************************************************/
    protected String getMacroExpansion(String text)
    {
        String expandedText = "";
        int lastEnd = 0;

        // Step through each macro in the text string
        for (MacroLocation location : getMacroLocation(text))
        {
            // Append the text leading to the macro name, then add macro value
            // in place of the name
            expandedText += text.substring(lastEnd, location.getStart())
                            + getMacroValue(location.getMacroName().replaceAll(MACRO_IDENTIFIER, ""));

            // Store the end position of the macro name for the next pass
            lastEnd = location.getStart() + location.getMacroName().length();
        }

        // Append any remaining text
        return expandedText += text.substring(lastEnd);
    }

    /**************************************************************************
     * Replace any macro names embedded in the supplied string array with the
     * associated macro values
     * 
     * @param array
     *            array of strings containing macro names
     * 
     * @return String array with any embedded macro names replaced with the
     *         associated macro values
     *************************************************************************/
    protected String[][] replaceAllMacros(String[][] array)
    {
        // Step through each row in the array
        for (int row = 0; row < array.length; row++)
        {
            // Step through each column in the row
            for (int column = 0; column < array[row].length; column++)
            {
                // Replace any macro names with the corresponding values
                array[row][column] = getMacroExpansion(array[row][column]);
            }
        }

        return array;
    }

    /**************************************************************************
     * Get a list of macros referenced in the specified text string
     * 
     * @param text
     *            text string containing macro names
     * 
     * @return List of macros referenced in the specified text string. A macro
     *         name only appears once in the list even if referenced multiple
     *         times in the text string
     *************************************************************************/
    protected List<String> getReferencedMacros(String text)
    {
        List<String> referenced = new ArrayList<String>();

        // Step through each macro in the text string
        for (MacroLocation location : getMacroLocation(text))
        {
            // Strip the macro delimiters from the name
            String macroName = location.getMacroName().replaceAll(MACRO_IDENTIFIER, "");

            // Check if the macro is not already in the list (case insensitive)
            if (!CcddUtilities.contains(macroName, referenced))
            {
                // Add the macro name to the list
                referenced.add(macroName);
            }
        }

        return referenced;
    }

    /**************************************************************************
     * Get a list containing the tables in the project database that reference
     * the specified macro name. Include references in the custom values table
     * 
     * @param macroName
     *            macro name for which to search
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return List containing the tables in the database that reference the
     *         specified macro name
     *************************************************************************/
    protected String[] getMacroReferences(String macroName, Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.SEARCH,
                                 new String[][] { {"_search_text_",
                                                   macroName},
                                                 {"_case_insensitive_",
                                                  "true"},
                                                 {"_selected_tables_",
                                                  SearchType.DATA.toString()}},
                                 parent);
    }

    /**************************************************************************
     * Highlight any macros in the the specified text component
     * 
     * @param component
     *            reference to the table cell renderer component
     * 
     * @param text
     *            cell value
     * 
     * @param hightlightColor
     *            color used for highlighting the macro name
     *************************************************************************/
    protected void highlightMacro(Component component,
                                  String text,
                                  Color hightlightColor)
    {
        // Get a reference to the highlighter
        Highlighter highlighter = ((JTextComponent) component).getHighlighter();

        // Create a highlighter painter
        DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(hightlightColor);

        // Remove any existing highlighting
        highlighter.removeAllHighlights();

        // Step through each macro location
        for (MacroLocation location : getMacroLocation(text))
        {
            try
            {
                // Highlight the macro name in the text
                highlighter.addHighlight(location.getStart(),
                                         location.getStart()
                                             + location.getMacroName().length(),
                                         painter);
            }
            catch (BadLocationException ble)
            {
                // Ignore highlighting failure
            }
        }
    }

    /**************************************************************************
     * Tool tip text showing any macro names embedded in the supplied text
     * replaced with the associated macro values
     * 
     * @param text
     *            text string containing macro names
     * 
     * @return Tool tip text string showing any embedded macro names in the
     *         supplied text replaced with the associated macro values
     *************************************************************************/
    protected String getMacroToolTipText(String text)
    {
        // Check if the text string contains any macros
        if (text != null && text.matches(".*"
                                         + MACRO_IDENTIFIER
                                         + ".+"
                                         + MACRO_IDENTIFIER
                                         + ".*"))
        {
            // Replace any macro names in the text with the associated macro
            // values
            text = getMacroExpansion(text);

            // Check if a cell is beneath the mouse pointer
            if (text != null)
            {
                // Expand any macros in the cell text and display this as
                // the cell's tool tip text
                text = CcddUtilities.wrapText("<html><i><b>Macro Expansion:</b></i>"
                                              + "<br><p style=\"margin-left: 5px\">"
                                              + text,
                                              ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize());
            }
        }
        // The text contains no macros
        else
        {
            // Set so there's no tool tip displayed
            text = null;
        }

        return text;
    }

    /**************************************************************************
     * Add new macros and check for matches with existing ones
     * 
     * @param macroDefinitions
     *            list of macro definitions
     * 
     * @return The name of the macro if its name matches an existing one but
     *         the macro value differs; return null if the macro is new or
     *         matches an existing one
     *************************************************************************/
    protected String updateMacros(List<String[]> macroDefinitions)
    {
        String badType = null;

        // Step through each imported macro definition
        for (String[] macroDefn : macroDefinitions)
        {
            // Get the macro value associated with this macro name
            String macro = getMacroValue(macroDefn[MacrosColumn.MACRO_NAME.ordinal()]);

            // Check if the macro doesn't already exist
            if (macro == null)
            {
                // Add the macro
                macros.add(macroDefn);
            }
            // The macro exists; check if the macro value provided matches the
            // existing macro value
            else if (!macro.equals(macroDefn[MacrosColumn.VALUE.ordinal()]))
            {
                // Store the name of the mismatched macro and stop
                // searching
                badType = macroDefn[MacrosColumn.MACRO_NAME.ordinal()];
                break;
            }
        }

        return badType;
    }
}
