/**
 * CFS Command and Data Dictionary macro handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.MACRO_IDENTIFIER;
import static CCDD.CcddConstants.SIZEOF_DATATYPE;
import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.SearchType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary macro handler class
 *************************************************************************************************/
public class CcddMacroHandler
{
    // Class references
    private CcddMain ccddMain;
    private final CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddVariableHandler variableHandler;

    // List containing the macro names and associated unexpanded values
    private List<String[]> macros;

    // Array containing the expanded macro values. Unless the macro's value definition changes the
    // expanded value remains the same. Using the stored data saves the time needed to reevaluate
    // the macro value
    private List<String> expandedMacroValues;

    // Macro name pattern
    private final Pattern macroPattern;

    // Flag that indicates if a macro is referenced in its parent path
    private boolean isMacroRecursive;

    // List containing the valid data types when evaluating sizeof() calls
    private List<String> invalidDataTypes;

    // List containing the macro pop-up combo box tool tips
    private final List<String> popUpToolTips;

    // List containing the macro modifications following an import operation
    private List<TableModification> modifications;

    // List containing the macro definitions following an import operation
    private List<String[]> updatedMacros;

    // List of macro references already loaded from the database. This is used to avoid repeated
    // searches for a the same macro
    private List<MacroReference> loadedReferences;

    /**********************************************************************************************
     * Macro data table references class
     *********************************************************************************************/
    protected class MacroReference
    {
        private final String macroName;
        private final String[] references;

        /******************************************************************************************
         * Macro data table references class constructor
         *
         * @param macroName
         *            macro name
         *
         * @param parent
         *            GUI component over which to center any error dialog
         *****************************************************************************************/
        MacroReference(String macroName, Component parent)
        {
            this.macroName = macroName;
            List<String> dependentMacros = new ArrayList<String>();
            String searchMacros = "";

            // Get the list of macros that have a value that depends on the supplied macro. The
            // list also contains the supplied macro name
            getDependentMacros(macroName, dependentMacros);

            // Step through each dependent macro name
            for (String refMacro : dependentMacros)
            {
                // Add the macro name, with delimiters, to the search criteria
                searchMacros += CcddMacroHandler.getFullMacroName(refMacro) + "|";
            }

            // Clean up the macro search name string
            searchMacros = CcddUtilities.removeTrailer(searchMacros, "|");

            // Get the references to the specified macro(s) in the data tables
            references = searchMacroReferences(searchMacros, parent);
        }

        /******************************************************************************************
         * Get the macro name associated with the references
         *
         * @return Macro name
         *****************************************************************************************/
        protected String getMacroName()
        {
            return macroName;
        }

        /******************************************************************************************
         * Get the references in the data tables for this macro
         *
         * @return References in the data tables for this macro
         *****************************************************************************************/
        protected String[] getReferences()
        {
            return references;
        }
    }

    /**********************************************************************************************
     * Macro location class
     *********************************************************************************************/
    private class MacroLocation
    {
        private final String macroName;
        private final int start;

        /******************************************************************************************
         * Macro location class constructor
         *
         * @param macroName
         *            macro name, including the macro delimiters
         *
         * @param start
         *            index of the beginning of the macro name in the text string
         *****************************************************************************************/
        MacroLocation(String macroName, int start)
        {
            this.macroName = macroName;
            this.start = start;
        }

        /******************************************************************************************
         * Get the macro name, including the macro delimiters
         *
         * @return Macro name, including the macro delimiters
         *****************************************************************************************/
        protected String getMacroName()
        {
            return macroName;
        }

        /******************************************************************************************
         * Get the index of the beginning of the macro name in the text string
         *
         * @return Index of the beginning of the macro name in the text string
         *****************************************************************************************/
        protected int getStart()
        {
            return start;
        }
    }

    /**********************************************************************************************
     * Macro handler class constructor used when setting the macros from a source other than those
     * in the project database
     *
     * @param ccddMain
     *            main class
     *
     * @param macros
     *            list of string arrays containing macro names and the corresponding macro values
     *********************************************************************************************/
    CcddMacroHandler(CcddMain ccddMain, List<String[]> macros)
    {
        this.ccddMain = ccddMain;
        this.macros = macros;
        tableTypeHandler = ccddMain.getTableTypeHandler();

        popUpToolTips = new ArrayList<String>();

        // Create the macro name search pattern
        macroPattern = Pattern.compile("^.*?("
                                       + MACRO_IDENTIFIER
                                       + "([^"
                                       + MACRO_IDENTIFIER
                                       + "]+)"
                                       + MACRO_IDENTIFIER
                                       + ").*$",
                                       Pattern.CASE_INSENSITIVE);

        // Initialize the expanded macro value array
        clearStoredValues();
    }

    /**********************************************************************************************
     * Macro handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddMacroHandler(CcddMain ccddMain)
    {
        // Load the macro table from the project database
        this(ccddMain,
             ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.MACROS,
                                                                          true,
                                                                          ccddMain.getMainFrame()));

        this.ccddMain = ccddMain;
    }

    /**********************************************************************************************
     * Set the reference to the variable size handler class
     *
     * @param variableHandler
     *            reference to the variable handler
     *
     * @param dataTypeHandler
     *            reference to the data type handler
     *********************************************************************************************/
    protected void setHandlers(CcddVariableHandler variableHandler,
                               CcddDataTypeHandler dataTypeHandler)
    {
        this.variableHandler = variableHandler;
        this.dataTypeHandler = dataTypeHandler;
    }

    /**********************************************************************************************
     * Get the macro data
     *
     * @return List of string arrays containing macro names and the corresponding macro values
     *********************************************************************************************/
    protected List<String[]> getMacroData()
    {
        return macros;
    }

    /**********************************************************************************************
     * Set the macro data to the supplied list of macro definitions
     *
     * @param macros
     *            list of string arrays containing macro names and the corresponding unexpanded
     *            macro values
     *********************************************************************************************/
    protected void setMacroData(List<String[]> macros)
    {
        this.macros = CcddUtilities.copyListOfStringArrays(macros);

        // Reinitialize the expanded macro value array
        clearStoredValues();
    }

    /**********************************************************************************************
     * Set the macro data to the list of updated macro definitions
     *********************************************************************************************/
    protected void setMacroData()
    {
        // Check if the macros are updated
        if (updatedMacros != null)
        {
            // Store the updated macros and remove the list of updates
            setMacroData(updatedMacros);
            updatedMacros = null;
        }
    }

    /**********************************************************************************************
     * Get the list of macro names
     *
     * @return List of macro names, sorted alphabetically; an empty list if no macros are defined
     *********************************************************************************************/
    protected List<String> getMacroNames()
    {
        // Create a list to hold the macro names
        List<String> macroNames = new ArrayList<String>();

        // Step through each macro
        for (String[] macro : macros)
        {
            // Store the macro name in the list
            macroNames.add(macro[MacrosColumn.MACRO_NAME.ordinal()]);
        }

        // Sort the macro names alphabetically
        Collections.sort(macroNames, String.CASE_INSENSITIVE_ORDER);

        return macroNames;
    }

    /**********************************************************************************************
     * Clear the list of expanded macro values. This should be done following any change to a
     * macro's unexpanded value so that the unexpanded value is reevaluated when next requested
     *********************************************************************************************/
    protected void clearStoredValues()
    {
        expandedMacroValues = new ArrayList<String>(macros.size());

        // Initialize the expanded macro values to null
        for (int index = 0; index < macros.size(); index++)
        {
            expandedMacroValues.add(null);
        }
    }

    /**********************************************************************************************
     * Get the the stored version of the supplied macro name. Macro names are case insensitive;
     * however the name as used in the internal table can be needed
     *
     * @param inputName
     *            name of the macro for which to get the stored name
     *
     * @return Macro name as stored in the internal macros table; null if the macro doesn't exist
     *********************************************************************************************/
    protected String getStoredMacroName(String inputName)
    {
        String storedName = null;

        // Step through each macro
        for (String[] macro : macros)
        {
            // Check if the supplied name matches the macro name, ignoring case
            if (macro[MacrosColumn.MACRO_NAME.ordinal()].equalsIgnoreCase(inputName))
            {
                // Set the name to the stored macro name and stop searching
                storedName = macro[MacrosColumn.MACRO_NAME.ordinal()];
                break;
            }
        }

        return storedName;
    }

    /**********************************************************************************************
     * Get the macro name encased in the macro identifier character(s)
     *
     * @param macroName
     *            macro name
     *
     * @return Macro name encased in the macro identifier character(s)
     *********************************************************************************************/
    protected static String getFullMacroName(String macroName)
    {
        return MACRO_IDENTIFIER + macroName + MACRO_IDENTIFIER;
    }

    /**********************************************************************************************
     * Get a list of all macro name locations in the specified text string
     *
     * @param text
     *            text string to search for macro names
     *
     * @return List of all macro name locations in the specified text string
     *********************************************************************************************/
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

                // Check if the pattern matches a defined macro. This uses the macro name without
                // the delimiters
                if (isMacroExists(matcher.group(2)))
                {
                    // Update the macro name starting position and remove the characters from the
                    // beginning of the text string to the end of the macro name
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
                    // Remove the first character in the text, advance the starting position by 1,
                    // and try matching again
                    text = text.substring(position + 1);
                    start += position + 1;
                }
            }
        } while (matcher.matches());
        // Process the text string until no macro names are found

        return locations;
    }

    /**********************************************************************************************
     * Replace all instances of the specified macro name in the supplied text string
     *
     * @param oldName
     *            original macro name, including the delimiters
     *
     * @param newName
     *            new macro name, including the delimiters
     *
     * @param text
     *            text string in which to replace the macro name
     *
     * @return The supplied text string with all references to the specified macro replaced by the
     *         new macro name
     *********************************************************************************************/
    protected String replaceMacroName(String oldName, String newName, String text)
    {
        // Get the locations of the macro(s) in the supplied string
        List<MacroLocation> locations = getMacroLocation(text);

        // Step through each macro in the text string. This is done in reverse since the start
        // indices after the current location change as the text string length changes
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

    /**********************************************************************************************
     * Replace all instances in all macros with sizeof() calls using the specified data type name
     * with the new data type name
     *
     * @param oldDataTypeName
     *            original data type name
     *
     * @param newDataTypeName
     *            new data type name
     *********************************************************************************************/
    protected void replaceDataTypeReferences(String oldDataTypeName, String newDataTypeName)
    {
        // Step through each macro definition
        for (int index = 0; index < macros.size(); index++)
        {
            // Check if the macro's value has a sizeof() call for the specified data type
            if (CcddVariableHandler.hasSizeof(macros.get(index)[MacrosColumn.VALUE.ordinal()],
                                              oldDataTypeName,
                                              CcddMacroHandler.this))
            {
                // Replace instances of the old data type name in any sizeof() calls with the new
                // name
                macros.set(index,
                           new String[] {macros.get(index)[MacrosColumn.MACRO_NAME.ordinal()],
                                         macros.get(index)[MacrosColumn.VALUE.ordinal()].replaceAll("(sizeof\\(+?\\s*)(?:"
                                                                                                    + oldDataTypeName
                                                                                                    + ")(\\s*\\))",
                                                                                                    "$1" + newDataTypeName + "$2")});
            }
        }
    }

    /**********************************************************************************************
     * Check for any recursive reference in the specified macro's value
     *
     * @param macroName
     *            macro name
     *
     * @return true if a recursive reference is detected in the macro's value
     *********************************************************************************************/
    protected boolean isMacroRecursive(String macroName)
    {
        // Get the macro's value, checking for recursion
        getMacroValue(macroName);

        return isMacroRecursive;
    }

    /**********************************************************************************************
     * Get the expanded value associated with the specified macro name. If the expanded value is
     * already known (from a previous value request) then this known value is used; otherwise the
     * macro's expanded value is evaluated from its unexpanded value
     *
     * @param macroName
     *            name of the macro for which the value is needed
     *
     * @return Expanded value associated with the specified macro name; returns null if the macro
     *         doesn't exist. The isMacroRecursive flag will be set to true if the macro contains a
     *         recursive reference
     *********************************************************************************************/
    protected String getMacroValue(String macroName)
    {
        // TODO NEED TO HANDLE MACROS IN THE FORMAT name(a[,b[,...]])

        String macroValue = null;
        isMacroRecursive = false;

        // Step through each defined macro
        for (int index = 0; index < macros.size(); index++)
        {
            // Check if the supplied name matches this macro's name and this is not a new macro.
            // When a macro is added the expanded macro value array size isn't updated immediately
            // so as not to erase the existing expanded macro values. Therefore the macro name may
            // be in the list but not in the array
            if (macroName.equalsIgnoreCase(macros.get(index)[MacrosColumn.MACRO_NAME.ordinal()]))
            {
                // Check if the macro's expanded value hasn't already been determined
                if (expandedMacroValues.get(index) == null)
                {
                    // Get the macro's value, replacing any embedded macros with their respective
                    // values and evaluating any sizeof() calls
                    macroValue = getMacroValue(macroName, new ArrayList<String>());

                    // Evaluate the text as a mathematical expression
                    Double exprResult = CcddMathExpressionHandler.evaluateExpression(macroValue);

                    // Check if the text is a valid mathematical expression
                    if (exprResult != null)
                    {
                        // Set the value to expression result
                        macroValue = String.valueOf((int) ((double) exprResult));
                    }

                    // Store the expanded macro value
                    expandedMacroValues.set(index, macroValue);
                }
                // The macro's expanded value is already determined
                else
                {
                    // Get the expanded macro value
                    macroValue = expandedMacroValues.get(index);
                }

                break;
            }
        }

        return macroValue;
    }

    /**********************************************************************************************
     * Get the value associated with the specified macro name. This is a recursive method for
     * macros referencing other macros
     *
     * @param macroName
     *            macro name
     *
     * @param referencedMacros
     *            list containing the macros references in a macro value path. This list is used to
     *            detect if a macro references itself, which would cause an infinite loop. Set to
     *            null to not test for recursion
     *
     * @return Value associated with the specified macro name; returns null if the macro doesn't
     *         exist
     *********************************************************************************************/
    private String getMacroValue(String macroName, List<String> referencedMacros)
    {
        String macroValue = null;

        // Check that a recursion error wasn't found; this prevents an infinite loop from occurring
        if (!isMacroRecursive)
        {
            // Check if the macro is referenced in the value path above it
            if (referencedMacros != null && referencedMacros.contains(macroName.toUpperCase()))
            {
                // Set the flag to indicate a recursive reference exists
                isMacroRecursive = true;
            }
            // The macro doesn't have a reference to itself in the path above it
            else
            {
                // Check if recursion is being checked
                if (referencedMacros != null)
                {
                    // Add the macro name to the list of those within the macro's value path
                    referencedMacros.add(0, macroName.toUpperCase());
                }

                // Step through each defined macro
                for (String[] macro : macros)
                {
                    // Check if the supplied name matches this macro's name
                    if (macroName.equalsIgnoreCase(macro[MacrosColumn.MACRO_NAME.ordinal()]))
                    {
                        // Replace each sizeof() call with its numeric value
                        macroValue = variableHandler.replaceSizeofWithValue(macro[MacrosColumn.VALUE.ordinal()],
                                                                            invalidDataTypes);

                        // Check if the sizeof() call references an invalid data type
                        if (variableHandler.isInvalidReference())
                        {
                            // Set the flag to indicate a recursive reference exists
                            isMacroRecursive = true;
                        }

                        // Get a list of macros referenced in this macro's value
                        List<String> refMacros = getReferencedMacros(macroValue);

                        // Check if any macros are referenced by this macro
                        if (!refMacros.isEmpty())
                        {
                            List<String> priorMacroRefs = null;

                            // Check if recursion is being checked
                            if (referencedMacros != null)
                            {
                                // Create a list to contain the macro references in the path prior
                                // to this reference
                                priorMacroRefs = new ArrayList<String>(referencedMacros);
                            }

                            // Step through each macro referenced by this macro
                            for (int argIndex = 0; argIndex < refMacros.size(); argIndex++)
                            {
                                // Get the value of the referenced macro
                                String value = getMacroValue(refMacros.get(argIndex),
                                                             priorMacroRefs);

                                // Replace all instances of the macro with its expanded value
                                macroValue = macroValue.replaceAll(MACRO_IDENTIFIER
                                                                   + refMacros.get(argIndex)
                                                                   + MACRO_IDENTIFIER,
                                                                   value);
                            }
                        }

                        break;
                    }
                }
            }
        }

        return macroValue;
    }

    /**********************************************************************************************
     * Check if the supplied text contains any macro references
     *
     * @param text
     *            text string containing macro names
     *
     * @return true if the text contains a macro reference
     *********************************************************************************************/
    protected static boolean hasMacro(String text)
    {
        return text != null && text.matches(".*"
                                            + MACRO_IDENTIFIER
                                            + ".+"
                                            + MACRO_IDENTIFIER
                                            + ".*");
    }

    /**********************************************************************************************
     * Check if the supplied macro name is already in use (case insensitive)
     *
     * @param macroName
     *            macro name
     *
     * @return true if the supplied macro name is already in use
     *********************************************************************************************/
    protected boolean isMacroExists(String macroName)
    {
        return getMacroIndex(macroName) != -1;
    }

    /**********************************************************************************************
     * Get the index for the macro with the supplied name (case insensitive)
     *
     * @param macroName
     *            macro name
     *
     * @return Index for the macro with the supplied name; -1 if no macro with this name exists
     *********************************************************************************************/
    private int getMacroIndex(String macroName)
    {
        int macroIndex = -1;

        // Step through each defined macro
        for (int index = 0; index < macros.size(); index++)
        {
            // Check if the macro name matches the supplied name
            if (macroName.equalsIgnoreCase(macros.get(index)[MacrosColumn.MACRO_NAME.ordinal()]))
            {
                // Store the macro's index and stop searching
                macroIndex = index;
                break;
            }
        }

        return macroIndex;
    }

    /**********************************************************************************************
     * Replace any macro names and sizeof() calls embedded in the supplied text with the associated
     * macro values and data type sizes
     *
     * @param text
     *            text string possibly containing macro names and/or sizeof() calls
     *
     * @return Text string with any embedded macro names and sizeof() calls replaced with the
     *         associated macro values and data type sizes
     *********************************************************************************************/
    protected String getMacroExpansion(String text)
    {
        return getMacroExpansion(text, null);
    }

    /**********************************************************************************************
     * Replace any macro names and sizeof() calls embedded in the supplied text with the associated
     * macro values and data type sizes. If a list of invalid data types is supplied, sizeof()
     * calls set an error flag if the referenced data type is in the list
     *
     * @param text
     *            text string possibly containing macro names and/or sizeof() calls
     *
     * @param invalidDataTypes
     *            List containing the invalid data types when evaluating sizeof() calls; null if
     *            there are no data type constraints for a sizeof() call
     *
     * @return Text string with any embedded macro names and sizeof() calls replaced with the
     *         associated macro values and data type sizes; if no macro or sizeof() call is present
     *         the text is returned unchanged
     *********************************************************************************************/
    protected String getMacroExpansion(String text, List<String> invalidDataTypes)
    {
        isMacroRecursive = false;

        String expandedText;
        int lastEnd = 0;

        // Check if the text string contains a macro or sizeof() call
        if (hasMacro(text) || CcddVariableHandler.hasSizeof(text))
        {
            expandedText = "";
            this.invalidDataTypes = invalidDataTypes;

            // Convert any sizeof() calls to the equivalent data type size
            text = variableHandler.replaceSizeofWithValue(text, invalidDataTypes);

            // Check if the sizeof() call references an invalid data type
            if (variableHandler.isInvalidReference())
            {
                // Set the flag to indicate a recursive reference exists
                isMacroRecursive = true;
            }

            // Step through each macro in the text string
            for (MacroLocation location : getMacroLocation(text))
            {
                // Append the text leading to the macro name, then add the macro value in place of
                // the name
                expandedText += text.substring(lastEnd, location.getStart())
                                + getMacroValue(location.getMacroName().replaceAll(MACRO_IDENTIFIER, ""));

                // Store the end position of the macro name for the next pass
                lastEnd = location.getStart() + location.getMacroName().length();
            }

            // Append any remaining text
            expandedText += text.substring(lastEnd);

            // Separate the text at any comma. This is to evaluate each substring to see if it's an
            // expression. This allows macros to represent array sizes for multi-dimensional arrays
            String[] parts = expandedText.split("\\s*,\\s*");

            // Check if there is no comma to separate the text (so that it's potentially a single
            // expression)
            if (parts.length == 1)
            {
                // Evaluate the text as a mathematical expression
                Double exprResult = CcddMathExpressionHandler.evaluateExpression(expandedText);

                // Check if the text is a valid mathematical expression
                if (exprResult != null)
                {
                    // Set the value to expression result
                    expandedText = String.valueOf((int) ((double) exprResult));
                }
            }
            // The string contains one or more commas. Each substring is evaluated as an expression
            else
            {
                boolean isExpr = true;
                String multiText = "";

                // Step through each substring
                for (String part : parts)
                {
                    // Evaluate the text as a mathematical expression
                    Double exprResult = CcddMathExpressionHandler.evaluateExpression(part);

                    // Check if the text is a valid mathematical expression
                    if (exprResult != null)
                    {
                        // Set the value to expression result
                        multiText += String.valueOf((int) ((double) exprResult)) + ",";
                    }
                    // The substring isn't an expression
                    else
                    {
                        // Set the flag to indicate that the text isn't comma-separated integers
                        // and stop checking
                        isExpr = false;
                        break;
                    }
                }

                // Check if the every substring is an expression
                if (isExpr)
                {
                    // Set the expanded text to the comma-separated integers, removing the trailing
                    // comma added above
                    expandedText = CcddUtilities.removeTrailer(multiText, ",").replaceAll(",", ", ");
                }
            }

            // Reset the invalid data types so this list doesn't inadvertently affect macro checks
            // where there is no data type constraint
            this.invalidDataTypes = null;
        }
        // The text doesn't contain a macro or sizeof() call
        else
        {
            // Return the text string as-is
            expandedText = text;
        }

        return expandedText;
    }

    /**********************************************************************************************
     * Replace any macro names embedded in the supplied string array with the associated macro
     * values
     *
     * @param array
     *            array of strings containing macro names
     *
     * @return String array with any embedded macro names replaced with the associated macro values
     *********************************************************************************************/
    protected Object[][] replaceAllMacros(Object[][] array)
    {
        // Step through each row in the array
        for (int row = 0; row < array.length; row++)
        {
            // Step through each column in the row
            for (int column = 0; column < array[row].length; column++)
            {
                // Replace any macro names with the corresponding values
                array[row][column] = array[row][column] instanceof String
                                                                          ? getMacroExpansion(array[row][column].toString())
                                                                          : array[row][column];
            }
        }

        return array;
    }

    /**********************************************************************************************
     * Get a list of macros referenced in the specified text string
     *
     * @param text
     *            text string containing macro names
     *
     * @return List of macros referenced in the specified text string. A macro name only appears
     *         once in the list even if referenced multiple times in the text string
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Get the list of structure names referenced in sizeof() calls in the specified macro
     *
     * @param macroName
     *            macro name
     *
     * @return List of structure names referenced in sizeof() calls in the specified macro macro;
     *         and empty list if no structures are referenced or the macro doesn't exist
     *********************************************************************************************/
    protected List<String> getStructureReferences(String macroName)
    {
        List<String> structureReferences = new ArrayList<String>();

        // Step through each macro
        for (String[] macroDefn : macros)
        {
            // Check if the macro name matches the target macro
            if (macroName.equalsIgnoreCase(macroDefn[MacrosColumn.MACRO_NAME.ordinal()]))
            {
                // Parse each data type referenced in a sizeof() call in the macro
                for (String dataType : macroDefn[MacrosColumn.VALUE.ordinal()].replaceAll(".*?"
                                                                                          + SIZEOF_DATATYPE
                                                                                          + ".*?",
                                                                                          "$1 ")
                                                                              .split(" "))
                {
                    // Check if the data type is a structure and the structure name isn't already
                    // in the list
                    if (!dataTypeHandler.isPrimitive(dataType)
                        && !structureReferences.contains(dataType))
                    {

                        // Add the structure name to the list
                        structureReferences.add(dataType);
                    }
                }
            }
        }

        return structureReferences;
    }

    /**********************************************************************************************
     * Get a list containing the macros that are dependent on the specified data type
     *
     * @param dataType
     *            data type name for which to search
     *
     * @return List containing the macros that are dependent on the specified data type
     *********************************************************************************************/
    protected List<String> getDataTypeReferences(String dataType)
    {
        List<String> references = new ArrayList<String>();

        // Step through each macro definition
        for (String[] macro : macros)
        {
            // Check if the macro's value has a sizeof() call for the specified data type
            if (CcddVariableHandler.hasSizeof(macro[MacrosColumn.VALUE.ordinal()],
                                              dataType,
                                              CcddMacroHandler.this))
            {
                // Add the macro and its dependent macros to the list
                getDependentMacros(macro[MacrosColumn.MACRO_NAME.ordinal()], references);
            }
        }

        return references;
    }

    /**********************************************************************************************
     * Add the name of specified macro and the names of all macros that depend on this macro's
     * value to the supplied list. This is a recursive method
     *
     * @param macroName
     *            name of the macro for which to find all dependent macros
     *
     * @param dependentMacros
     *            List of macros that have a value that is dependent on a specified macro (list
     *            includes the specified macro)
     *********************************************************************************************/
    private void getDependentMacros(String macroName, List<String> dependentMacros)
    {
        // Check if the macro hasn't already been processed and added to the list
        if (!dependentMacros.contains(macroName))
        {
            // Add the macro name to the list
            dependentMacros.add(macroName);

            // Step through each macro definition
            for (String[] macro : macros)
            {
                // Check that this isn't the definition for the specified macro. Since a macro is
                // programatically prevented from referencing itself (since that would constitute a
                // recursion error) this check simply prevents making unneeded calls below
                if (!macro[MacrosColumn.MACRO_NAME.ordinal()].equalsIgnoreCase(macroName))
                {
                    // Get a list of every macro name referenced in this macro's value
                    List<String> refMac = getReferencedMacros(macro[MacrosColumn.VALUE.ordinal()]);

                    // Check if this macro's value references the specified macro (making its value
                    // dependent on the specified macro)
                    if (refMac.contains(macroName))
                    {
                        // Add this macro and its dependents to the list
                        getDependentMacros(macro[MacrosColumn.MACRO_NAME.ordinal()],
                                           dependentMacros);
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Get a list containing the search results in the project database for tables that reference
     * the specified macro name. Include references in the custom values table
     *
     * @param macroName
     *            macro name for which to search
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List containing the search results in the project database for tables that reference
     *         the specified macro name
     *********************************************************************************************/
    protected String[] searchMacroReferences(String macroName, Component parent)
    {
        // Get the references in the prototype tables that match the specified macro name
        List<String> matches = new ArrayList<String>(Arrays.asList(ccddMain.getDbCommandHandler()
                                                                           .getList(DatabaseListCommand.SEARCH,
                                                                                    new String[][] {{"_search_text_",
                                                                                                     "("
                                                                                                                      + macroName.replaceAll("([\\(\\)])",
                                                                                                                                             "\\\\\\\\$1")
                                                                                                                      + ")"},
                                                                                                    {"_case_insensitive_",
                                                                                                     "true"},
                                                                                                    {"_allow_regex_",
                                                                                                     "true"},
                                                                                                    {"_selected_tables_",
                                                                                                     SearchType.DATA.toString()},
                                                                                                    {"_columns_",
                                                                                                     ""}},
                                                                                    parent)));

        // Remove any references to the macro that appear in an array size column for an array
        // member (the reference in the array's definition is all that's needed)
        CcddSearchHandler.removeArrayMemberReferences(matches, tableTypeHandler);

        return matches.toArray(new String[0]);
    }

    /**********************************************************************************************
     * (Re)initialize the lists for the macro modifications and macro definitions. This must be
     * called prior to the first call to updateMacros(), or to clear the lists
     *********************************************************************************************/
    protected void initializeMacroUpdates()
    {
        modifications = new ArrayList<TableModification>();
        updatedMacros = CcddUtilities.copyListOfStringArrays(macros);
    }

    /**********************************************************************************************
     * Add new macros and check for matches with existing ones
     *
     * @param macroDefinitions
     *            list of macro definitions
     *
     * @param replaceExisting
     *            true to replace the value for an existing macro
     *
     * @return List of macro names for macros with values that differ between the existing macro
     *         and the supplied definitions
     *********************************************************************************************/
    protected List<String> updateMacros(List<String[]> macroDefinitions, boolean replaceExisting)
    {
        List<String> mismatchedMacros = new ArrayList<String>();

        // Step through each imported macro definition
        for (String[] macroDefn : macroDefinitions)
        {
            // Get the index of the macro in the existing list
            int macroIndex = getMacroIndex(macroDefn[MacrosColumn.MACRO_NAME.ordinal()]);

            // Check if the macro by this name doesn't exist
            if (macroIndex == -1)
            {
                // Add the new macro to the existing ones
                updatedMacros.add(macroDefn);
            }
            // The macro exists. Check if the values differ
            else if (!macroDefn[MacrosColumn.VALUE.ordinal()].equals(macros.get(macroIndex)[MacrosColumn.VALUE.ordinal()]))
            {
                // Check if existing values are allowed to be changed
                if (replaceExisting)
                {
                    // Replace the value of the existing macro and add the modification information
                    // to the list
                    updatedMacros.get(macroIndex)[MacrosColumn.VALUE.ordinal()] = macroDefn[MacrosColumn.VALUE.ordinal()];
                    modifications.add(new TableModification(macroDefn, macros.get(macroIndex)));
                }
                // Value differences aren't allowed
                else
                {
                    // Add the macro name to the list of those with differing values
                    mismatchedMacros.add(macroDefn[MacrosColumn.MACRO_NAME.ordinal()]);
                }
            }
        }

        return mismatchedMacros;
    }

    /**********************************************************************************************
     * Initialize the list of already loaded macro references. This list is used when macros are
     * altered via the macro editor or an import operation
     *********************************************************************************************/
    protected void initializeReferences()
    {
        loadedReferences = new ArrayList<MacroReference>();
    }

    /**********************************************************************************************
     * Get the references to the specified macro in the tables. A list of loaded reference results
     * is maintained so that a previous search for a macro can be reused (initializeReferences()
     * must be called to clear this list, such as after a macro has been altered)
     *
     * @param macroName
     *            macro name
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Reference to the specified macro in the tables
     *********************************************************************************************/
    protected MacroReference getMacroReferences(String macroName, Component parent)
    {
        MacroReference macroRefs = null;

        // Step through the list of the macro search references already loaded
        for (MacroReference loadedRef : loadedReferences)
        {
            // Check if the macro name matches that for an already searched macro
            if (macroName.equals(loadedRef.getMacroName()))
            {
                // Store the macro search reference and stop searching
                macroRefs = loadedRef;
                break;
            }
        }

        // Check if the macro references haven't already been loaded
        if (macroRefs == null)
        {
            // Search for references to this macro
            macroRefs = new MacroReference(macroName, parent);

            // Add the search results to the list so that this search doesn't get performed again
            loadedReferences.add(macroRefs);
        }

        return macroRefs;
    }

    /**********************************************************************************************
     * Get the list containing the name of every table that references the specified macro
     *
     * @param macroName
     *            macro name
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Reference to the specified macro in the tables; an empty list if the macro is unused
     *********************************************************************************************/
    protected List<String> getMacroUsage(String macroName, Component parent)
    {
        List<String> tablePaths = new ArrayList<String>();

        // Step through each reference to the macro in the tables
        for (String macroRef : getMacroReferences(macroName, parent).getReferences())
        {
            // Split the reference into table name, column name, comment, and context
            String[] tblColCmtAndCntxt = macroRef.split(TABLE_DESCRIPTION_SEPARATOR, 4);
            String refComment = tblColCmtAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()];

            // Check if the this is a reference to a prototype data table
            if (!refComment.isEmpty())
            {
                // Extract the viewable name and type of the table and the name of the column
                // containing the data type, and separate the column string into the individual
                // column values
                String[] refNameAndType = refComment.split(",");

                // Check if the table name hasn't already been added to the list
                if (!tablePaths.contains(refNameAndType[0]))
                {
                    // Add the table name to the list of those using the macro
                    tablePaths.add(refNameAndType[0]);
                }
            }
            // The reference is in the custom values table
            else
            {
                // Extract the context from the reference
                String[] refColumns = CcddUtilities.splitAndRemoveQuotes(tblColCmtAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                // Get the path to the parent table for the reference in the custom values table
                String table = refColumns[ValuesColumn.TABLE_PATH.ordinal()];
                table = table.substring(0, table.lastIndexOf(","));

                // Check if the table name hasn't already been added to the list
                if (!tablePaths.contains(table))
                {
                    // Add the table name to the list of those using the macro
                    tablePaths.add(table);
                }
            }
        }

        return tablePaths;
    }

    /**********************************************************************************************
     * Verify that the updated macros are valid for each instance where the macro is used (e.g., a
     * table column with an input type of "Integer" can't accept a text string)
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @throws CCDDException
     *             If a macro's value is invalid in an instance where the macro is used
     *********************************************************************************************/
    protected void validateMacroUsage(Component parent) throws CCDDException
    {
        // Initialize the list of macro references already loaded
        initializeReferences();

        // Create a macro handler using the values currently displayed in the macro editor
        CcddMacroHandler newMacroHandler = new CcddMacroHandler(ccddMain, updatedMacros);
        newMacroHandler.setHandlers(variableHandler, dataTypeHandler);

        // Step through each updated macro definition
        for (String[] macro : updatedMacros)
        {
            // Verify the macro's usage
            validateMacroUsage(CcddMacroHandler.getFullMacroName(macro[MacrosColumn.MACRO_NAME.ordinal()]),
                               newMacroHandler,
                               parent);
        }
    }

    /**********************************************************************************************
     * Verify that the updated macros are valid for each instance where the macro is used (e.g., a
     * table column with an input type of "Integer" can't accept a text string)
     *
     * @param macroName
     *            name of the macro to validate
     *
     * @param newMacroHandler
     *            reference to the macro handler that incorporates the macro updates
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @throws CCDDException
     *             If a macro's value is invalid in an instance where the macro is used
     *********************************************************************************************/
    protected void validateMacroUsage(String macroName,
                                      CcddMacroHandler newMacroHandler,
                                      Component parent) throws CCDDException
    {
        List<String> tableNames = new ArrayList<String>();

        // Step through each reference to the macro in the tables
        for (String macroRef : getMacroReferences(macroName, parent).getReferences())
        {
            // Split the reference into table name, column name, table type, and context
            String[] tblColDescAndCntxt = macroRef.split(TABLE_DESCRIPTION_SEPARATOR, 4);
            String refComment = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()];

            // Check if the this is a reference to a prototype data table
            if (!refComment.isEmpty())
            {
                // Extract the viewable name and type of the table and the name of the column
                // containing the data type, and separate the column string into the individual
                // column values
                String[] refNameAndType = refComment.split(",");

                // Check if this table hasn't already been found to contain a type mismatch
                if (!tableNames.contains(refNameAndType[0]))
                {
                    String refColumn = tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()];
                    String[] refContext = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                    // Use the type and column to get the column's input type
                    TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(refNameAndType[1]);
                    int columnIndex = typeDefn.getColumnIndexByDbName(refColumn);
                    InputType inputType = typeDefn.getInputTypes()[columnIndex];

                    // The referenced column value has the original macro names, and these may have
                    // been altered in the editor. In order to evaluate the updated macro value the
                    // macro names in the reference must be replaced by their updated names. The
                    // macro's OID column value is used to get the new name from the old name. Step
                    // through each macro referenced, using the original names, in the column value
                    for (String oldName : getReferencedMacros(refContext[columnIndex]))
                    {
                        String newName = oldName;
                        String oid = "";

                        // Step through the updated macro definitions
                        for (String[] oldMacro : getMacroData())
                        {
                            // Check if the macro names match
                            if (oldName.equals(oldMacro[MacrosColumn.MACRO_NAME.ordinal()]))
                            {
                                // Store the OID value for the macro and stop searching
                                oid = oldMacro[MacrosColumn.OID.ordinal()];
                                break;
                            }
                        }

                        // Step through the updated macro definitions
                        for (String[] newMacro : newMacroHandler.getMacroData())
                        {
                            // Check if the macro's OID value matches the target one
                            if (oid.equals(newMacro[MacrosColumn.OID.ordinal()]))
                            {
                                // Since the OIDs match these are the same macro. Store the macro's
                                // new name (in case it changed) and stop searching
                                newName = newMacro[MacrosColumn.MACRO_NAME.ordinal()];
                                break;
                            }
                        }

                        // Replace all instances of the macro's old name with its new name
                        refContext[columnIndex] = replaceMacroName(CcddMacroHandler.getFullMacroName(oldName),
                                                                   CcddMacroHandler.getFullMacroName(newName),
                                                                   refContext[columnIndex]);
                    }

                    // Check if the expanded value of the updated macro doesn't match the input
                    // type required by the macro's user
                    if (!newMacroHandler.getMacroExpansion(refContext[columnIndex]).matches(inputType.getInputMatch()))
                    {
                        // Add the affected table name to the list
                        tableNames.add(refNameAndType[0]);
                    }
                }
            }
        }

        // Check if any tables with conflicts with the new data type were found
        if (!tableNames.isEmpty())
        {
            throw new CCDDException("Macro value is not consistent with macro usage in table(s) '</b>"
                                    + CcddUtilities.convertArrayToStringTruncate(tableNames.toArray(new String[0]))
                                    + "<b>'");
        }
    }

    /**********************************************************************************************
     * Update all existing macro instances where the macro name or value has been changed. This
     * method relies on calling updateMacros() to set the lists of modifications and updated macros
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @throws CCDDException
     *             If an error occurs updating an existing reference to a modified macro
     *********************************************************************************************/
    protected void updateExistingMacroUsage(Component parent) throws CCDDException
    {
        // Perform the macro updates and check if an error occurred
        if (ccddMain.getDbTableCommandHandler().modifyTablesPerDataTypeOrMacroChanges(modifications,
                                                                                      updatedMacros,
                                                                                      parent))

        {
            throw new CCDDException();
        }

        // Step through each open table editor dialog
        for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
        {
            // Step through each individual editor
            for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
            {
                // Force the table to redraw to update the macro highlighting
                editor.getTable().repaint();
            }
        }
    }

    /**********************************************************************************************
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
     *********************************************************************************************/
    protected void highlightMacro(Component component, String text, Color hightlightColor)
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
                                         location.getStart() + location.getMacroName().length(),
                                         painter);
            }
            catch (BadLocationException ble)
            {
                // Ignore highlighting failure
            }
        }
    }

    /**********************************************************************************************
     * Tool tip text showing any macro names embedded in the supplied text replaced with the
     * associated macro values
     *
     * @param text
     *            text string containing macro names
     *
     * @return Tool tip text string showing any embedded macro names in the supplied text replaced
     *         with the associated macro values
     *********************************************************************************************/
    protected String getMacroToolTipText(String text)
    {
        // Check if the text string contains any macros
        if (text != null
            && (hasMacro(text) || CcddVariableHandler.hasSizeof(text)))
        {
            // Replace any macro names in the text with the associated macro values
            text = getMacroExpansion(text);

            // Check if a cell is beneath the mouse pointer
            if (text != null)
            {
                // Expand any macros in the cell text and display this as the cell's tool tip text
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

    /**********************************************************************************************
     * Get the list of items to display in the macro pop-up combo box
     *
     * @param textComp
     *            text component over which to display the pop-up combo box and insert the selected
     *            macro name
     *
     * @param inputType
     *            input type of the text component (InputType)
     *
     * @param validDataTypes
     *            list of valid data types from which to choose
     *
     * @return List of items to display in the macro pop-up combo box
     *********************************************************************************************/
    protected List<String> getMacroPopUpItems(JTextComponent textComp,
                                              InputType inputType,
                                              List<String> validDataTypes)
    {
        List<String> validMacros = new ArrayList<String>();
        popUpToolTips.clear();

        // Check if any macros exist
        if (!macros.isEmpty())
        {
            // Step through each macro
            for (String[] macro : macros)
            {
                // Get the text component's text with the macro value replacing the macro name
                String text = textComp.getText().substring(0, textComp.getSelectionStart())
                              + macro[MacrosColumn.VALUE.ordinal()]
                              + textComp.getText().substring(textComp.getSelectionEnd());

                // Initialize the parentheses counters. For each left (right) parenthesis a right
                // (left) one is added to the end (beginning) of the string. This ensures the
                // parentheses are balanced if the number of left and right parentheses are unequal
                // or if the one or more right parentheses precedes the first left parenthesis.
                // Note that this may not match the user's intended final arrangement for the
                // parentheses; it serves only to ensure that the resulting string a valid formula.
                // The macro is then evaluated in this context to see if the resulting string
                // matches the specified input type and thus determine if teh macro is included in
                // the pop-up menu
                int leftParenthesisCount = 0;
                int rightParenthesisCount = 0;

                // Step through each character in the text string
                for (char c : text.toCharArray())
                {
                    // Check if this is a left parenthesis
                    if (c == '(')
                    {
                        // Increment the left parenthesis counter
                        leftParenthesisCount++;
                    }
                    // Check if this is a right parenthesis
                    else if (c == ')')
                    {
                        // Increment the parenthesis counter
                        rightParenthesisCount++;
                    }
                }

                // Perform for the number of left parentheses
                while (leftParenthesisCount > 0)
                {
                    // Append a left parenthesis and decrement the counter
                    text += ")";
                    leftParenthesisCount--;
                }

                // Perform for the number of right parentheses
                while (rightParenthesisCount > 0)
                {
                    // Prepend a left parenthesis and decrement the counter
                    text = "(" + text;
                    rightParenthesisCount--;
                }

                // Create a string version of the new value, replacing any macro in the text with
                // its corresponding value
                text = getMacroExpansion(text, validDataTypes);

                // Check if the text component's text, with the macro's value inserted, is allowed
                // in the target text component based on the component's input type
                if ((text.isEmpty() || text.matches(inputType.getInputMatch()))
                    && !isMacroRecursive)
                {
                    // Add the macro name to the list with its value as the item's tool tip text
                    validMacros.add(macro[MacrosColumn.MACRO_NAME.ordinal()]);
                    popUpToolTips.add(macro[MacrosColumn.VALUE.ordinal()]);
                }
            }
        }

        return validMacros;
    }

    /**********************************************************************************************
     * Get the list of tool tips for the items in the macro pop-up combo box
     *
     * @return List of tool tips for the items in the macro pop-up combo box
     *********************************************************************************************/
    protected List<String> getMacroPopUpToolTips()
    {
        return popUpToolTips;
    }
}
