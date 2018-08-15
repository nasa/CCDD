/**
 * CFS Command & Data Dictionary data type handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.SIZEOF_DATATYPE;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.JTextComponent;

import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.SearchType;

/**************************************************************************************************
 * CFS Command & Data Dictionary data type handler class
 *************************************************************************************************/
public class CcddDataTypeHandler
{
    // Class references
    private CcddDbTableCommandHandler dbTable;
    private CcddDbCommandHandler dbCommand;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddMacroHandler macroHandler;

    // Pop-up combo box for displaying the structure names and the dialog to contain it
    private PaddedComboBox structureCbox;
    private JDialog comboDlg;

    // List containing the data type names and associated data type definitions
    private List<String[]> dataTypes;

    /**********************************************************************************************
     * Data type handler class constructor used when setting the data types from a source other
     * than those in the project database
     *
     * @param dataTypes
     *            list of string arrays containing data type names and the corresponding data type
     *            definitions
     *********************************************************************************************/
    CcddDataTypeHandler(List<String[]> dataTypes)
    {
        this.dataTypes = dataTypes;
    }

    /**********************************************************************************************
     * Data type handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddDataTypeHandler(CcddMain ccddMain)
    {
        // Load the data types table from the project database
        this(ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.DATA_TYPES,
                                                                          true,
                                                                          ccddMain.getMainFrame()));
        // Get references to make subsequent calls shorter
        dbTable = ccddMain.getDbTableCommandHandler();
        dbCommand = ccddMain.getDbCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
    }

    /**********************************************************************************************
     * Get the data type definitions
     *
     * @return List of string arrays containing the data type definitions
     *********************************************************************************************/
    protected List<String[]> getDataTypeData()
    {
        return dataTypes;
    }

    /**********************************************************************************************
     * Determine if the specified column contains integer values
     *
     * @return true if the specified column contains integer values; false otherwise
     *********************************************************************************************/
    private boolean isInteger(int column)
    {
        return column == DataTypeEditorColumnInfo.SIZE.ordinal();
    }

    /**********************************************************************************************
     * Get the data type definitions as an object array. The object array allows preserving the
     * column value's type (string, integer, etc.)
     *
     * @return Data type definitions as an object array
     *********************************************************************************************/
    protected Object[][] getDataTypeDataArray()
    {
        // Create storage for the data type definitions
        Object[][] dataTypesArray = new Object[dataTypes.size()][DataTypeEditorColumnInfo.values().length];

        // Step through each data type definition
        for (int row = 0; row < dataTypes.size(); row++)
        {
            // Step through each column in the data type definition
            for (int column = 0; column < DataTypeEditorColumnInfo.values().length; column++)
            {
                // Store the column value as a string or integer
                dataTypesArray[row][column] = isInteger(column)
                                                                ? Integer.valueOf(dataTypes.get(row)[column].toString())
                                                                : dataTypes.get(row)[column].toString();
            }
        }

        return dataTypesArray;
    }

    /**********************************************************************************************
     * Set the data types to the supplied array
     *
     * @param dataTypes
     *            list of string arrays containing data type names and the corresponding data type
     *            definitions
     *********************************************************************************************/
    protected void setDataTypeData(List<String[]> dataTypes)
    {
        this.dataTypes = new ArrayList<String[]>(dataTypes);
    }

    /**********************************************************************************************
     * Get the data type name. Return the user-defined name unless it's blank, in which case return
     * the C-language name
     *
     * @param dataType
     *            string array containing data type name and the corresponding data type definition
     *
     * @return User-defined data type name; if blank then the C-language name
     *********************************************************************************************/
    protected static String getDataTypeName(String[] dataType)
    {
        return getDataTypeName(dataType[DataTypesColumn.USER_NAME.ordinal()],
                               dataType[DataTypesColumn.C_NAME.ordinal()]);
    }

    /**********************************************************************************************
     * Get the data type name. Return the user-defined name unless it's blank, in which case return
     * the C-language name
     *
     * @param userName
     *            user-defined data type name
     *
     * @param cName
     *            C-language data type
     *
     * @return User-defined data type name; if blank then the C-language name
     *********************************************************************************************/
    protected static String getDataTypeName(String userName, String cName)
    {
        String dataTypeName = null;

        // Check if the user-defined name is blank
        if (userName.isEmpty())
        {
            // Get the C-language name
            dataTypeName = cName;
        }
        // User-defined name isn't blank
        else
        {
            // Get the user-defined name
            dataTypeName = userName;
        }

        return dataTypeName;
    }

    /**********************************************************************************************
     * Get the data type information associated with the specified data type name
     *
     * @param dataTypeName
     *            data type name
     *
     * @return Data type information associated with the specified data type name; returns null if
     *         the data type doesn't exist
     *********************************************************************************************/
    protected String[] getDataTypeByName(String dataTypeName)
    {
        String[] dataType = null;

        // Step through each defined data type
        for (String[] type : dataTypes)
        {
            // Check if the supplied name matches this data type's name
            if (dataTypeName.equalsIgnoreCase(getDataTypeName(type)))
            {
                // Store the data type information and stop searching
                dataType = type;
                break;
            }
        }

        return dataType;
    }

    /**********************************************************************************************
     * Get the base data type for the specified data type
     *
     * @param dataTypeName
     *            data type name
     *
     * @return Base data type for the specified data type; returns null if the data type doesn't
     *         exist
     *********************************************************************************************/
    protected BaseDataTypeInfo getBaseDataType(String dataTypeName)
    {
        BaseDataTypeInfo baseDataType = null;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeByName(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Get the associated base data type
            baseDataType = BaseDataTypeInfo.getBaseType(dataType[DataTypesColumn.BASE_TYPE.ordinal()]);
        }

        return baseDataType;
    }

    /**********************************************************************************************
     * Get the data type size for the specified data type
     *
     * @param dataTypeName
     *            data type name
     *
     * @return Data type size for the specified data type; returns 0 if the data type doesn't exist
     *********************************************************************************************/
    protected int getDataTypeSize(String dataTypeName)
    {
        int dataTypeSize = 0;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeByName(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Get the associated data type size
            dataTypeSize = Integer.valueOf(dataType[DataTypesColumn.SIZE.ordinal()]);
        }

        return dataTypeSize;
    }

    /**********************************************************************************************
     * Get the data type size in bytes for the specified data type
     *
     * @param dataTypeName
     *            data type name
     *
     * @return Data type size in bytes for the specified data type; returns 0 if the data type
     *         doesn't exist
     *********************************************************************************************/
    protected int getSizeInBytes(String dataTypeName)
    {
        // Get the data type size
        int dataTypeSize = getDataTypeSize(dataTypeName);

        // Check if this data type is a character string
        if (isString(dataTypeName))
        {
            // Force the size to 1 byte. This prevents the string pseudo-data type, which uses a
            // size other than 1 to indicate the data type is a string, from returning an incorrect
            // size
            dataTypeSize = 1;
        }

        return dataTypeSize;
    }

    /**********************************************************************************************
     * Get the data type size in bits for the specified data type
     *
     * @param dataTypeName
     *            data type name
     *
     * @return Data type size in bits for the specified data type; returns 0 if the data type
     *         doesn't exist
     *********************************************************************************************/
    protected int getSizeInBits(String dataTypeName)
    {
        return getSizeInBytes(dataTypeName) * 8;
    }

    /**********************************************************************************************
     * Determine if the supplied data type is a primitive type
     *
     * @param dataTypeName
     *            name of data type to test
     *
     * @return true if the supplied data type is a primitive
     *********************************************************************************************/
    protected boolean isPrimitive(String dataTypeName)
    {
        boolean isPrimitive = false;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeByName(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Set the flag to indicate the data type is a primitive
            isPrimitive = true;
        }

        return isPrimitive;
    }

    /**********************************************************************************************
     * Determine if the specified data type is a signed or unsigned integer
     *
     * @param dataTypeName
     *            data type name
     *
     * @return true if the specified data type is a signed or unsigned integer
     *********************************************************************************************/
    protected boolean isInteger(String dataTypeName)
    {
        boolean isInteger = false;

        // get the base data type for the specified data type
        BaseDataTypeInfo baseDataType = getBaseDataType(dataTypeName);

        // Set the flag to true if the base data type is an integer (signed or unsigned)
        isInteger = baseDataType == BaseDataTypeInfo.UNSIGNED_INT
                    || baseDataType == BaseDataTypeInfo.SIGNED_INT;

        return isInteger;
    }

    /**********************************************************************************************
     * Determine if the the specified data type is a signed integer
     *
     * @param dataTypeName
     *            data type name
     *
     * @return true if the specified data type is a signed integer
     *********************************************************************************************/
    protected boolean isSignedInt(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.SIGNED_INT;
    }

    /**********************************************************************************************
     * Determine if the the specified data type is an unsigned integer
     *
     * @param dataTypeName
     *            data type name
     *
     * @return true if the specified data type is an unsigned integer
     *********************************************************************************************/
    protected boolean isUnsignedInt(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.UNSIGNED_INT;
    }

    /**********************************************************************************************
     * Determine if the specified data type is a float or double
     *
     * @param dataTypeName
     *            data type name
     *
     * @return true if the specified data type is a float or double
     *********************************************************************************************/
    protected boolean isFloat(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.FLOATING_POINT;
    }

    /**********************************************************************************************
     * Determine if the this primitive data type is a character or string
     *
     * @param dataTypeName
     *            data type name
     *
     * @return true if this data type is a character or string
     *********************************************************************************************/
    protected boolean isCharacter(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.CHARACTER;
    }

    /**********************************************************************************************
     * Determine if the this primitive data type is a character string
     *
     * @param dataTypeName
     *            data type name
     *
     * @return true if this data type is a character string
     *********************************************************************************************/
    protected boolean isString(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.CHARACTER
               && getDataTypeSize(dataTypeName) > 1;
    }

    /**********************************************************************************************
     * Determine if the this primitive data type is a pointer
     *
     * @param dataTypeName
     *            data type name
     *
     * @return true if this data type is a pointer
     *********************************************************************************************/
    protected boolean isPointer(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.POINTER;
    }

    /**********************************************************************************************
     * Get the minimum possible value of the primitive type based on the data type and size in
     * bytes
     *
     * @param dataTypeName
     *            data type name
     *
     * @return Minimum possible value of the primitive type based on the data type and size in
     *         bytes
     *********************************************************************************************/
    protected Object getMinimum(String dataTypeName)
    {
        Object minimum = 0;

        // Get the data type size in bytes
        int bytes = getSizeInBytes(dataTypeName);

        // Check if the data type is an unsigned integer
        if (isUnsignedInt(dataTypeName))
        {
            minimum = (long) 0;
        }
        // Check if the data type is a signed integer (an unsigned integer was already accounted
        // for above)
        else if (isInteger(dataTypeName))
        {
            minimum = (long) (-(long) Math.pow(2, bytes * 8) / 2);
        }
        // Check if the data type is a floating point
        else if (isFloat(dataTypeName))
        {
            // Use the Java float and double minimum values
            minimum = bytes == 4
                                 ? (float) -Float.MAX_VALUE
                                 : bytes == 8
                                              ? (double) -Double.MAX_VALUE
                                              : 0;
        }

        return minimum;
    }

    /**********************************************************************************************
     * Get the maximum possible value of the primitive type based on the data type and size in
     * bytes
     *
     * @param dataTypeName
     *            data type name
     *
     * @return Maximum possible value of the primitive type based on the data type and size in
     *         bytes
     *********************************************************************************************/
    protected Object getMaximum(String dataTypeName)
    {
        Object maximum = 0;

        // Get the data type size in bytes
        int bytes = getSizeInBytes(dataTypeName);

        // Check if the data type is an unsigned integer
        if (isUnsignedInt(dataTypeName))
        {
            maximum = (long) Math.pow(2, bytes * 8);
        }
        // Check if the data type is a signed integer (an unsigned integer was already accounted
        // for above)
        else if (isInteger(dataTypeName))
        {
            long maxUnsigned = (long) Math.pow(2, bytes * 8);
            maximum = (long) (maxUnsigned - maxUnsigned / 2 + 1);
        }
        // Check if the data type is a floating point
        else if (isFloat(dataTypeName))
        {
            // Use the Java float and double maximum values
            maximum = bytes == 4
                                 ? (float) Float.MAX_VALUE
                                 : bytes == 8
                                              ? (double) Double.MAX_VALUE
                                              : 0;
        }

        return maximum;
    }

    /**********************************************************************************************
     * Get a list containing the tables in the project database that reference the specified data
     * type name. Only search for references in the prototype tables (any references in the custom
     * values table are automatically updated when the prototype is changed)
     *
     * @param dataTypeName
     *            data type name for which to search
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List containing the tables in the database that reference the specified data type
     *         name
     *********************************************************************************************/
    protected String[] getDataTypeReferences(String dataTypeName, Component parent)
    {
        String searchCriteria = dataTypeName;

        // Step through each macro with a value that is dependent on this data type
        for (String macroName : macroHandler.getDataTypeReferences(dataTypeName))
        {
            // Add the macro name to the search criteria
            searchCriteria += "|" + macroName;
        }

        // Get the references in the prototype tables that match the specified data type name
        List<String> matches = new ArrayList<String>(Arrays.asList(dbCommand.getList(DatabaseListCommand.SEARCH,
                                                                                     new String[][] {{"_search_text_",
                                                                                                      "(" + searchCriteria + ")"},
                                                                                                     {"_case_insensitive_",
                                                                                                      "true"},
                                                                                                     {"_allow_regex_",
                                                                                                      "true"},
                                                                                                     {"_selected_tables_",
                                                                                                      SearchType.PROTO.toString()},
                                                                                                     {"_columns_",
                                                                                                      ""}},
                                                                                     parent)));

        // Remove any references to the data type that appear in an array size column for an array
        // member (the reference in the array's definition is all that's needed)
        CcddSearchHandler.removeArrayMemberReferences(matches, tableTypeHandler);

        return matches.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Add new data types and check for matches with existing ones
     *
     * @param dataTypeDefinitions
     *            list of data type definitions
     *
     * @throws CCDDException
     *             If an data type with the same same already exists and the imported type doesn't
     *             match
     *********************************************************************************************/
    protected void updateDataTypes(List<String[]> dataTypeDefinitions) throws CCDDException
    {
        // Step through each imported data type definition
        for (String[] typeDefn : dataTypeDefinitions)
        {
            // Get the data type information associated with this data type name
            String[] dataType = getDataTypeByName(CcddDataTypeHandler.getDataTypeName(typeDefn));

            // Check if the data type doesn't already exist
            if (dataType == null)
            {
                // Add the data type
                dataTypes.add(typeDefn);
            }
            // The data type exists; check if the type information provided matches the existing
            // type information
            else if (!(dataType[DataTypesColumn.USER_NAME.ordinal()].equals(typeDefn[DataTypesColumn.USER_NAME.ordinal()])
                       && dataType[DataTypesColumn.C_NAME.ordinal()].equals(typeDefn[DataTypesColumn.C_NAME.ordinal()])
                       && dataType[DataTypesColumn.SIZE.ordinal()].equals(typeDefn[DataTypesColumn.SIZE.ordinal()])
                       && dataType[DataTypesColumn.BASE_TYPE.ordinal()].equals(typeDefn[DataTypesColumn.BASE_TYPE.ordinal()])))
            {
                throw new CCDDException("Imported data type '</b>"
                                        + CcddDataTypeHandler.getDataTypeName(typeDefn)
                                        + "<b>' doesn't match the existing definition");
            }
        }
    }

    /**********************************************************************************************
     * Highlight any sizeof() calls in the the specified text component
     *
     * @param component
     *            reference to the table cell renderer component
     *
     * @param text
     *            cell value
     *
     * @param hightlightColor
     *            color used for highlighting the sizeof() call
     *********************************************************************************************/
    protected static void highlightSizeof(Component component, String text, Color hightlightColor)
    {
        // Highlight 'sizeof(data type)' instances. Create a highlighter painter
        DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(hightlightColor);

        // Create the match pattern
        Pattern pattern = Pattern.compile(SIZEOF_DATATYPE);

        // Create the pattern matcher from the pattern
        Matcher matcher = pattern.matcher(text);

        // Check if there is a match in the cell value
        while (matcher.find())
        {
            try
            {
                // Highlight the matching text. Adjust the highlight color to account for the cell
                // selection highlighting so that the sizeof() call is easily readable
                ((JTextComponent) component).getHighlighter().addHighlight(matcher.start(),
                                                                           matcher.end(),
                                                                           painter);
            }
            catch (BadLocationException ble)
            {
                // Ignore highlighting failure
            }
        }
    }

    /**********************************************************************************************
     * Display a pop-up combo box containing the names of the defined data types: primitive data
     * types (dependent on the input flag) and structures. When the user selects a data type insert
     * it into the supplied text field
     *
     * @param owner
     *            dialog owning the pop-up combo box
     *
     * @param textArea
     *            text field over which to display the pop-up combo box and insert the selected
     *            data type name
     *
     * @param includePrimitives
     *            true to include primitive data types in the list; false to include only
     *            structures
     *
     * @param structures
     *            Array containing the data types to display in the pop-up; null to build the array
     *********************************************************************************************/
    protected void insertDataTypeName(Window owner,
                                      final JTextArea textArea,
                                      boolean includePrimitives,
                                      String[] structures)
    {
        comboDlg = new JDialog(owner);

        // Check if the data type array isn't supplied
        if (structures == null)
        {
            // Get the array of prototype structure table names
            structures = dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE);

            // Check if any structures exist
            if (structures.length != 0)
            {
                // Sort the structure names alphabetically, ignoring case
                Arrays.sort(structures, String.CASE_INSENSITIVE_ORDER);
            }

            // Check if primitive data types are to be included
            if (includePrimitives)
            {
                String[] primitives = new String[dataTypes.size()];

                // Step through each primitive data type
                for (int index = 0; index < dataTypes.size(); index++)
                {
                    // Add the data type name to the array
                    primitives[index] = getDataTypeName(dataTypes.get(index));
                }

                // Combine the primitive data types and structures arrays
                structures = CcddUtilities.concatenateArrays(primitives, structures);
            }
        }

        // Check if any data types exist
        if (structures.length != 0)
        {
            // Create the pop-up combo box
            structureCbox = new PaddedComboBox(structures,
                                               ModifiableFontInfo.DATA_TABLE_CELL.getFont());

            // Enable item matching for the combo box
            structureCbox.enableItemMatching(null);

            // Set the first structure as initially selected
            structureCbox.setSelectedIndex(0);

            // Set the property to allow the arrow keys to be used to change the structure
            // selection in the combo box
            structureCbox.putClientProperty("JComboBox.isTableCellEditor",
                                            Boolean.TRUE);

            // Add a listener for selection events in the structure pop-up combo box
            structureCbox.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a selection event in the structure combo box
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Get the selected structure's name and enclose it in the structure identifier
                    // character(s)
                    String structureName = ((JComboBox<?>) ae.getSource()).getSelectedItem().toString();

                    // Get the starting index of the selected text in the field
                    int start = textArea.getSelectionStart();

                    // Insert the structure into the text field's existing text, overwriting any of
                    // the text that is highlighted
                    textArea.setText(getInsertedStructure(structureName, textArea));
                    textArea.setSelectionStart(start);

                    // Select the structure name that was inserted
                    textArea.setSelectionEnd(start + structureName.length());

                    // Remove the structure pop-up and return to the caller. Get the selected
                    // structure's name and enclose it in the structure identifier character(s)
                    exitStructCombo();
                }
            });

            // Add a listener for key press events in the structure pop-up combo box
            structureCbox.addKeyListener(new KeyAdapter()
            {
                /**********************************************************************************
                 * Handle a key press event in the structure combo box
                 *********************************************************************************/
                @Override
                public void keyPressed(KeyEvent ke)
                {
                    // Check if the escape key is pressed
                    if (ke.getKeyCode() == KeyEvent.VK_ESCAPE)
                    {
                        // Remove the structure pop-up and return to the caller
                        exitStructCombo();
                    }
                }
            });

            // Add a listener for changes to the expansion/contraction of the combo box
            structureCbox.addPopupMenuListener(new PopupMenuListener()
            {
                /**********************************************************************************
                 * Handle a combo box expansion event
                 *********************************************************************************/
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent pme)
                {
                }

                /**********************************************************************************
                 * Handle a combo box contraction event
                 *********************************************************************************/
                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent pme)
                {
                }

                /**********************************************************************************
                 * Handle a combo box cancel event. This occurs if the mouse is clicked outside the
                 * combo box
                 *********************************************************************************/
                @Override
                public void popupMenuCanceled(PopupMenuEvent pme)
                {
                    // Remove the structure pop-up and return to the caller
                    exitStructCombo();
                }
            });

            // Create the dialog to contain the structure pop-up combo box. Set to modeless so that
            // pop-up dialog focus changes can be detected
            comboDlg.setModalityType(ModalityType.MODELESS);
            comboDlg.setUndecorated(true);
            comboDlg.add(structureCbox, BorderLayout.NORTH);
            comboDlg.setSize(new Dimension(structureCbox.getPreferredSize()));

            // Add a listener for focus changes to the pop-up dialog
            comboDlg.addWindowFocusListener(new WindowFocusListener()
            {
                /**********************************************************************************
                 * Handle a gain of pop-up dialog focus
                 *********************************************************************************/
                @Override
                public void windowGainedFocus(WindowEvent we)
                {
                    // Create a runnable object to be executed
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        /**************************************************************************
                         * Execute after all pending Swing events are finished. This ensures the
                         * pop-up is showing and can be expanded
                         *************************************************************************/
                        @Override
                        public void run()
                        {
                            // Expand the combo box when it appears
                            structureCbox.showPopup();
                        }
                    });
                }

                /**********************************************************************************
                 * Handle a loss of pop-up dialog focus
                 *********************************************************************************/
                @Override
                public void windowLostFocus(WindowEvent we)
                {
                    // Remove the structure pop-up and return to the caller
                    exitStructCombo();
                }
            });

            // Position and display the pop-up
            positionStructurePopup(textArea);
            comboDlg.setVisible(true);
        }
    }

    /**********************************************************************************************
     * Position the dialog containing the structure pop-up combo box at the text cursor position in
     * the text field
     *
     * @param textArea
     *            text field over which to display the pop-up combo box
     *********************************************************************************************/
    private void positionStructurePopup(JTextArea textArea)
    {
        try
        {
            // Get the position of the text cursor within the text field
            Rectangle popUp = textArea.modelToView(textArea.getCaretPosition());

            // Position the pop-up at the text cursor position
            comboDlg.setLocation(textArea.getLocationOnScreen().x + popUp.x,
                                 textArea.getLocationOnScreen().y);
        }
        catch (BadLocationException ble)
        {
            // Position the pop-up at the left end of the text field
            comboDlg.setLocation(textArea.getLocationOnScreen().x,
                                 textArea.getLocationOnScreen().y);
        }
    }

    /**********************************************************************************************
     * Get the text of the specified text field with the structure name or value inserted at the
     * current selection point
     *
     * @param text
     *            structure name or value
     *
     * @param textArea
     *            text field over which the pop-up combo box is displayed
     *
     * @return Text of the specified text field with the structure name or value inserted at the
     *         current selection point
     *********************************************************************************************/
    private String getInsertedStructure(String text, JTextArea textArea)
    {
        // Insert the text into the text field at the selection start position, replacing any
        // characters between the selection start and end positions
        return textArea.getText().substring(0, textArea.getSelectionStart())
               + text
               + textArea.getText().substring(textArea.getSelectionEnd());
    }

    /**********************************************************************************************
     * Remove the structure pop-up combo box and return to the caller
     *********************************************************************************************/
    private void exitStructCombo()
    {
        comboDlg.setVisible(false);
        comboDlg.dispose();
    }
}
