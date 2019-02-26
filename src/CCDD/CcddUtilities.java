/**
 * CFS Command and Data Dictionary utilities.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.FLAGGED;
import static CCDD.CcddConstants.POSTGRESQL_RESERVED_CHARS;
import static CCDD.CcddConstants.SPLIT_IGNORE_QUOTES;
import static CCDD.CcddConstants.STRING_LIST_TEXT_SEPARATOR;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary utilities class
 *************************************************************************************************/
public class CcddUtilities
{
    // Command line quote type
    private static enum QuoteType
    {
        NO_QUOTE,
        SINGLE_QUOTE,
        DOUBLE_QUOTE
    };

    /**********************************************************************************************
     * HTML tag information class
     *********************************************************************************************/
    private static enum HTMLTag
    {
        // The order of the HTML enumerations prevents accidental conversion of special characters
        // within a tag from being interpreted as a tag character (e.g., the '<' in '<br>' would be
        // converted to a '&lt;' if the break tag appears prior to the less than tag in the list)
        AMP("&amp;", "&"),
        LESS("&lt;", "<"),
        GREAT("&gt;", ">"),
        SPACE("&#160;", " "),
        HTML("<html>", ""),
        BREAK("<br>", "\n");

        private final String htmlTag;
        private final String tagChar;

        /******************************************************************************************
         * HTML tag information class constructor
         *
         * @param htmlTag
         *            HTML tag characters
         *
         * @param tagChar
         *            HTML tag replacement character for HTML reserved characters
         *****************************************************************************************/
        HTMLTag(String htmlTag, String tagChar)
        {
            this.htmlTag = htmlTag;
            this.tagChar = tagChar;
        }

        /******************************************************************************************
         * Get the HTML tag
         *
         * @return HTML tag characters
         *****************************************************************************************/
        private String getHTMLTag()
        {
            return htmlTag;
        }

        /******************************************************************************************
         * Check if the HTML tag represents a reserved character
         *
         * @return true if the HTML tag represents a reserved character
         *****************************************************************************************/
        private boolean isReservedChar()
        {
            return !tagChar.isEmpty();
        }

        /******************************************************************************************
         * Replace the HTML reserved characters with the equivalent HTML tag
         *
         * @param inputText
         *            text string in which to replace the HTML characters
         *
         * @return The input text with the HTML reserved characters replaced with the equivalent
         *         HTML tag
         *****************************************************************************************/
        private String replaceReservedChar(String inputText)
        {
            // Check if this tag represents a reserved character
            if (isReservedChar())
            {
                // Replace all occurrences of the character with its HTML equivalent
                inputText = inputText.replaceAll(Pattern.quote(tagChar), htmlTag);
            }

            return inputText;
        }

        /******************************************************************************************
         * Get the HTML reserved character placeholder
         *
         * @return HTML reserved character placeholder
         *****************************************************************************************/
        private String getSpecialCharPlaceholder()
        {
            // Return a space for the HTML space tag; otherwise return an underscore
            return this.equals(SPACE) ? " " : "_";
        }
    }

    /**********************************************************************************************
     * HTML tag storage class. HTML tags that are removed from the input string are store in this
     * object along with the string index location
     *********************************************************************************************/
    private static class Tags
    {
        private final int index;
        private final String tag;

        /******************************************************************************************
         * HTML tag storage class constructor
         *
         * @param index
         *            location of the tag within the string
         *
         * @param tag
         *            tag text
         *****************************************************************************************/
        Tags(int index, String tag)
        {
            this.index = index;
            this.tag = tag;
        }

        /******************************************************************************************
         * Get the location of the tag within the string
         *
         * @return The location of the tag within the string
         *****************************************************************************************/
        private int getIndex()
        {
            return index;
        }

        /******************************************************************************************
         * Get the tag text
         *
         * @return The tag text
         *****************************************************************************************/
        private String getTag()
        {
            return tag;
        }
    }

    /**********************************************************************************************
     * Convert the specified string to a float
     *
     * @param value
     *            string in the format # or #/#
     *
     * @return Floating point representation of the value
     *********************************************************************************************/
    protected static float convertStringToFloat(String value)
    {
        float result;

        // Check if the value is a fraction (#/#)
        if (value.contains("/"))
        {
            // Separate the numerator and denominator and perform the conversion of each part
            String[] temp = value.split("/");
            result = Float.valueOf(temp[0]) / Float.valueOf(temp[1]);
        }
        // Not a fraction
        else
        {
            // Perform the conversion
            result = Float.valueOf(value);
        }

        return result;
    }

    /**********************************************************************************************
     * Check if two arrays contain the same set of items. The order of the items in each set has no
     * effect on the match outcome
     *
     * @param array1
     *            first array to compare
     *
     * @param array2
     *            second array to compare
     *
     * @return true if the two arrays contain the same items
     *********************************************************************************************/
    protected static boolean isArraySetsEqual(Object[] array1, Object[] array2)
    {
        boolean isEqual = false;

        // Check if the array sizes are the same
        if (array1.length == array2.length)
        {
            // Check if both arrays are empty
            if (array1.length == 0)
            {
                isEqual = true;
            }
            // Both arrays contain a member and have the same number of members
            else
            {
                // Convert the item arrays into lists for comparison purposes
                HashSet<Object> set1 = new HashSet<Object>(Arrays.asList(array1));
                HashSet<Object> set2 = new HashSet<Object>(Arrays.asList(array2));

                // Return true if all items exist in both arrays (the order of the items in the
                // arrays doesn't matter)
                isEqual = set1.equals(set2);
            }
        }

        return isEqual;
    }

    /**********************************************************************************************
     * Repeat any embedded double quotes, then bound the supplied text with double quotes
     *
     * @param text
     *            text string for which to add double quotes; a null string is replaced by a blank
     *
     * @return The supplied text string with any embedded double quotes repeated, and bounded with
     *         double quotes
     *********************************************************************************************/
    protected static String addEmbeddedQuotes(String text)
    {
        // Replace a null text string with a blank
        if (text == null)
        {
            text = "";
        }

        return "\"" + text.replaceAll("\"", "\"\"") + "\"";
    }

    /**********************************************************************************************
     * For each of the supplied text strings repeat any embedded double quotes, then bound with
     * double quotes and separate each result with a comma
     *
     * @param texts
     *            text string(s) for which to add double quotes and comma-separate
     *
     * @return Each of the supplied text strings with any embedded double quotes repeated, then
     *         bound with double quotes and separate each result with a comma
     *********************************************************************************************/
    protected static String addEmbeddedQuotesAndCommas(String... texts)
    {
        String output = "";

        // Step through each supplied text string
        for (String text : texts)
        {
            // Repeat any double quotes, bound the result in double quotes, and append a comma
            output += addEmbeddedQuotes(text) + ",";
        }

        return removeTrailer(output, ",");
    }

    /**********************************************************************************************
     * Split the supplied text string into an array, divided at commas, ignoring commas within
     * quotes. Remove the excess double quotes from the array members
     *
     * @param text
     *            text string to split
     *
     * @return Text string divided into separate components, split at commas (including any
     *         surrounding white space characters), accounting for commas within double quotes, and
     *         with the excess double quotes removed
     *********************************************************************************************/
    protected static String[] splitAndRemoveQuotes(String text)
    {
        return splitAndRemoveQuotes(text, "\\s*,\\s*", -1, true);
    }

    /**********************************************************************************************
     * Split the supplied text string into an array, divided at the specified separator
     * character(s), ignoring the separator character(s) within quotes. Remove the excess double
     * quotes from the array members
     *
     * @param text
     *            text string to split
     *
     * @param separator
     *            character(s) at which to split the text
     *
     * @param limit
     *            maximum number of parts to separate the text into. This is the number of parts
     *            returned, with any missing parts returned as blanks. Set to -1 to split the text
     *            into as many parts as there are separators in the text
     *
     * @param removeQuotes
     *            true to remove excess double quotes from the individual array members
     *
     * @return Text string divided into separate components, split at commas, accounting for commas
     *         within double quotes, and with the excess double quotes removed
     *********************************************************************************************/
    protected static String[] splitAndRemoveQuotes(String text,
                                                   String separator,
                                                   int limit,
                                                   boolean removeQuotes)
    {
        // Split the supplied string into the specified number of parts using the specified
        // separator
        String[] array = text.split(separator + SPLIT_IGNORE_QUOTES, limit);

        // Check if the excess double quotes are to be removed
        if (removeQuotes)
        {
            // Step through each definition entry
            for (int index = 0; index < array.length; index++)
            {
                // Remove any excess quotes
                array[index] = removeExcessQuotes(array[index]);
            }
        }

        return array;
    }

    /**********************************************************************************************
     * Convert the supplied array of text items into a single string with the items separated by
     * commas. If the length of the string exceeds a specified maximum then shorten the string to
     * the maximum, find the last comma, truncate the string, and add an indication of how many
     * other items are in the list
     *
     * @param itemArray
     *            array of text items to combine
     *
     * @return The array of text items converted to a single, comma-separated string, and shortened
     *         if above a maximum length
     *********************************************************************************************/
    protected static String convertArrayToStringTruncate(String[] itemArray)
    {
        // Convert the array of items into a single string
        String names = convertArrayToString(itemArray);

        // Check if the length of the item string exceeds the specified maximum
        if (names.length() > ModifiableSizeInfo.MAX_DIALOG_MESSAGE_LENGTH.getSize())
        {
            // Shorten the item list to the maximum length and find the index to the last comma,
            // which separates the items
            names = names.substring(0, ModifiableSizeInfo.MAX_DIALOG_MESSAGE_LENGTH.getSize());
            int index = names.lastIndexOf(",");

            // Check if a comma exists
            if (index != -1)
            {
                // Remove any partial item remaining after the truncation, along with the last
                // comma, and add text to indicate how many other items are in the original list
                names = names.substring(0, index);
                names += " ... and " + (itemArray.length - names.split(",").length) + " others";
            }
        }

        return names;
    }

    /**********************************************************************************************
     * Separate a command line string into an array of arguments. Account for quoting (single and
     * double) of arguments within the string
     *
     * @param argString
     *            command line argument string
     *
     * @return Array containing the separate command line arguments with any quotes removed
     *********************************************************************************************/
    protected static String[] parseCommandLine(String argString)
    {
        String[] argArray;

        // Check if the command line string is empty
        if (argString == null || argString.trim().isEmpty())
        {
            // Create an empty the argument array
            argArray = new String[0];
        }
        // The command line string isn't empty
        else
        {
            ArrayList<String> result = new ArrayList<String>();
            StringBuilder current = new StringBuilder();
            boolean isLastTokenQuoted = false;
            QuoteType activeQuote = QuoteType.NO_QUOTE;

            // Create a tokenizer using single and double quotes, and a space as the tokens
            StringTokenizer tokenizer = new StringTokenizer(argString, "\"\' ", true);

            // Continue to parse the string as long as one of the tokens is present in the argument
            // string
            while (tokenizer.hasMoreTokens())
            {
                // Get the next argument or delimiter
                String token = tokenizer.nextToken();

                switch (activeQuote)
                {
                    // Single quote is active
                    case SINGLE_QUOTE:
                        // Check if this is the ending single quote
                        if (token.equals("\'"))
                        {
                            // Reset the active quote to 'none'
                            activeQuote = QuoteType.NO_QUOTE;
                            isLastTokenQuoted = true;
                        }
                        // This is the continuation of a single-quoted argument
                        else
                        {
                            // Add the argument to the list
                            current.append(token);
                        }

                        break;

                    // Double quote is active
                    case DOUBLE_QUOTE:
                        // Check if this is the ending double quote
                        if (token.equals("\""))
                        {
                            // Reset the active quote to 'none'
                            activeQuote = QuoteType.NO_QUOTE;
                            isLastTokenQuoted = true;
                        }
                        // This is the continuation of a double-quoted argument
                        else
                        {
                            // Add the argument to the list
                            current.append(token);
                        }

                        break;

                    // No quote is active
                    case NO_QUOTE:
                        // Check if the this is the starting single quote
                        if (token.equals("\'"))
                        {
                            // Argument is bounded by single quotes
                            activeQuote = QuoteType.SINGLE_QUOTE;
                        }
                        // Check if the this is the starting double quote
                        else if (token.equals("\""))
                        {
                            // Argument is bounded by double quotes
                            activeQuote = QuoteType.DOUBLE_QUOTE;
                        }
                        // Check if this is the argument separator
                        else if (token.equals(" "))
                        {
                            // Check if this is a space within a quoted argument and the argument
                            // isn't blank
                            if (isLastTokenQuoted || current.length() != 0)
                            {
                                // Add the argument to the list
                                result.add(current.toString());
                                current.setLength(0);
                            }
                        }
                        // This is a command line argument (or a at least a portion of one if
                        // within quotes)
                        else
                        {
                            // Add the argument(s) to the list
                            current.append(token);
                        }

                        isLastTokenQuoted = false;
                        break;
                }
            }

            // Check if there is any remaining arguments text
            if (isLastTokenQuoted || current.length() != 0)
            {
                // Add the argument(s) to the list
                result.add(current.toString());
            }

            // Check if a quote is still in effect
            if (activeQuote == QuoteType.SINGLE_QUOTE || activeQuote == QuoteType.DOUBLE_QUOTE)
            {
                // Alert the user that the number of quotes is unbalanced
                throw new RuntimeException("unbalanced quotes in " + argString);
            }

            // Convert the argument list into an array
            argArray = result.toArray(new String[0]);
        }

        return argArray;
    }

    /**********************************************************************************************
     * Determine the character that separates an enumeration value from its corresponding label
     *
     * @param enumeration
     *            {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]}
     *
     * @return Character that separates an enumeration value from its corresponding label
     *********************************************************************************************/
    protected static String getEnumeratedValueSeparator(String enumeration)
    {
        String separator = null;

        // Check if the enumeration is in the expected format
        if (enumeration.matches("^\\s*\\d+\\s*.+$"))
        {
            // Extract the enumerated value separator character
            separator = enumeration.replaceFirst("^\\s*\\d+\\s*", "").substring(0, 1);
        }

        return separator;
    }

    /**********************************************************************************************
     * Determine the character that separates the enumerated pairs
     *
     * @param enumeration
     *            {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]}
     *
     * @param enumValueSeparator
     *            character used to separate an enumeration value from its corresponding label
     *
     * @return Character that separates the enumerated pairs
     *********************************************************************************************/
    protected static String getEnumerationPairSeparator(String enumeration,
                                                        String enumValueSeparator)
    {
        String separator = null;

        // Check if the enumeration is in the expected format
        if (enumeration.matches("^\\s*\\d+\\s*"
                                + enumValueSeparator
                                + "\\s*.+\\d+\\s*"
                                + enumValueSeparator
                                + "\\s*.+$"))
        {
            // Separate the enumeration at the value+enumerated value separator characters
            String[] parts = enumeration.split("\\s*\\d+\\s*" + Pattern.quote(enumValueSeparator));

            // Determine the length of the second array member. This consists of the first
            // enumerated value followed by the enumerated pair separator character. Extract the
            // ending character which is the enumerated pair separator
            int index = parts[1].length();
            separator = parts[1].substring(index - 1, index);
        }

        return separator;
    }

    /**********************************************************************************************
     * Create a copy of an array of string arrays
     *
     * @param array
     *            array of string arrays to copy
     *
     * @return Array containing a copy of the string arrays from the input array
     *********************************************************************************************/
    protected static String[][] copyArrayOfStringArrays(String[][] array)
    {
        // Create storage for the copy
        String[][] arrayCopy = new String[array.length][];

        // Step through each array in the list
        for (int row = 0; row < array.length; row++)
        {
            // Add a copy of the array to the list copy
            arrayCopy[row] = Arrays.copyOf(array[row], array[row].length);
        }

        return arrayCopy;
    }

    /**********************************************************************************************
     * Create a copy of a list of string arrays
     *
     * @param list
     *            list of string arrays to copy
     *
     * @return List containing a copy of the string arrays from the input list
     *********************************************************************************************/
    protected static List<String[]> copyListOfStringArrays(List<String[]> list)
    {
        // Create storage for the copy
        List<String[]> listCopy = new ArrayList<String[]>(list.size());

        // Step through each array in the list
        for (String[] item : list)
        {
            // Add a copy of the array to the list copy
            listCopy.add(Arrays.copyOf(item, item.length));
        }

        return listCopy;
    }

    /**********************************************************************************************
     * Create a copy of a single dimensional array with a specified number of extra columns
     * appended
     *
     * @param array
     *            array to copy
     *
     * @param numColumns
     *            number of columns to append
     *
     * @return Array containing the data from the input array plus the specified number of extra,
     *         empty columns appended
     *********************************************************************************************/
    protected static String[] appendArrayColumns(Object[] array, int numColumns)
    {
        // Force the number of added columns to be non-negative
        if (numColumns < 0)
        {
            numColumns = 0;
        }

        // Create the new array with the number of specified extra columns
        String[] newArray = new String[array.length + numColumns];

        // Initialize the array to blanks (this populates the added column(s))
        Arrays.fill(newArray, "");

        // Step through each column in the input array
        for (int column = 0; column < array.length; column++)
        {
            // Copy the input array value to the new array
            newArray[column] = array[column].toString();
        }

        return newArray;
    }

    /**********************************************************************************************
     * Create a copy of a two-dimensional array with a specified number of extra columns appended
     *
     * @param array
     *            array to copy
     *
     * @param numColumns
     *            number of columns to append
     *
     * @return Array containing the data from the input array plus the specified number of extra,
     *         empty columns appended
     *********************************************************************************************/
    protected static String[][] appendArrayColumns(Object[][] array, int numColumns)
    {
        // Create the new array with the number of specified extra columns
        String[][] newArray = new String[array.length][array[0].length + numColumns];

        // Step through each row in the input array
        for (int row = 0; row < array.length; row++)
        {
            // Initialize the row to blanks (this populates the added column(s))
            Arrays.fill(newArray[row], "");

            // Step through each column in the input array
            for (int column = 0; column < array[row].length; column++)
            {
                // Copy the input array value to the new array
                newArray[row][column] = array[row][column].toString();
            }
        }

        return newArray;
    }

    /**********************************************************************************************
     * Create a copy of a list of string arrays with the specified column removed
     *
     * @param list
     *            list from which to remove the column
     *
     * @param delColumn
     *            column to remove
     *
     * @return List of string arrays containing the data from the input list minus the specified
     *         column
     *********************************************************************************************/
    protected static List<String[]> removeArrayListColumn(List<String[]> list, int delColumn)
    {
        // Create the new list
        List<String[]> newList = new ArrayList<String[]>();

        // Step through each row in the input list
        for (String[] row : list)
        {
            // Create storage for the row minus the specified column
            List<String> newRow = new ArrayList<String>();

            // Step through each column in the row
            for (int column = 0; column < row.length; column++)
            {
                // Check that this isn't the column being deleted
                if (column != delColumn)
                {
                    // Copy the input array value to the new array
                    newRow.add(row[column]);
                }
            }

            // Add the row, minus the specified column, to the list copy
            newList.add(newRow.toArray(new String[0]));
        }

        return newList;
    }

    /**********************************************************************************************
     * Move the specified array member to the specified position in the array
     *
     * @param array
     *            array
     *
     * @param indexToMove
     *            index of the array member to move
     *
     * @param targetIndex
     *            new index for the moved array member
     *
     * @return Array with the specified member moved to the new position and the other members
     *         adjusted to accommodate the move
     *********************************************************************************************/
    protected static Object[] moveArrayMember(Object[] array, int indexToMove, int targetIndex)
    {
        // Copy the array. This ensures that the object array is of the same class as the input
        // array
        Object[] tempArray = Arrays.copyOf(array, array.length);

        // Check if the move is from a lower to a higher (or same) index
        if (indexToMove <= targetIndex)
        {
            // Step through each member of the array, from lowest to highest index
            for (int oldIndex = 0, newIndex = 0; oldIndex < array.length; oldIndex++, newIndex++)
            {
                // Copy the array member from its original position to its adjusted position
                tempArray[newIndex] = array[oldIndex];

                // Check if this is the array member to move
                if (oldIndex == indexToMove)
                {
                    // Decrement the index to copy to
                    newIndex--;
                }

                // Check if this is the new position for the array member
                if (oldIndex == targetIndex)
                {
                    // Increment the index to copy to
                    newIndex++;
                }
            }
        }
        // The move is from a higher to a lower index
        else
        {
            // Step through each member of the array, from highest to lowest index
            for (int oldIndex = array.length - 1, newIndex = array.length - 1; oldIndex >= 0; oldIndex--, newIndex--)
            {
                // Copy the array member from its original position to its adjusted position
                tempArray[newIndex] = array[oldIndex];

                // Check if this is the array member to move
                if (oldIndex == indexToMove)
                {
                    // Increment the index to copy to
                    newIndex++;
                }

                // Check if this is the new position for the array member
                if (oldIndex == targetIndex)
                {
                    // Decrement the index to copy to
                    newIndex--;
                }
            }
        }

        // Copy the array member to its new location in the array copy
        tempArray[targetIndex] = array[indexToMove];

        return tempArray;
    }

    /**********************************************************************************************
     * Concatenate the contents of two one-dimensional arrays to produce a third array
     *
     * @param <T>
     *            generic type parameter
     *
     * @param array1
     *            first array to combine (can be null)
     *
     * @param array2
     *            second array to combine
     *
     * @return One-dimensional array with the contents of the second array appended to the first
     *         array
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected static <T> T[] concatenateArrays(T[] array1, T[] array2)
    {
        T[] concatArray;

        // Check if the first array is empty
        if (array1 == null || array1.length == 0)
        {
            // Return the second array
            concatArray = array2;
        }
        // The first array isn't empty
        else
        {
            // Get the number of rows in each input array
            int numRows1 = array1.length;
            int numRows2 = array2.length;

            // Create storage for the combined array
            concatArray = (T[]) Array.newInstance(array1.getClass().getComponentType(),
                                                  numRows1 + numRows2);

            // Copy the input arrays into the proper location in the combined array
            System.arraycopy(array1, 0, concatArray, 0, numRows1);
            System.arraycopy(array2, 0, concatArray, numRows1, numRows2);
        }

        return concatArray;
    }

    /**********************************************************************************************
     * Concatenate the contents of two two-dimensional arrays to produce a third array
     *
     * @param <T>
     *            generic type parameter
     *
     * @param array1
     *            first array to combine (can be null)
     *
     * @param array2
     *            second array to combine
     *
     * @return Two-dimensional array with the contents of the second array appended to the first
     *         array
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected static <T> T[][] concatenateArrays(T[][] array1, T[][] array2)
    {
        T[][] concatArray;

        // Check if the first array is empty
        if (array1 == null || array1.length == 0)
        {
            // Return the second array
            concatArray = array2;
        }
        // The first array isn't empty
        else
        {
            // Get the number of rows in each input array
            int numRows1 = array1.length;
            int numRows2 = array2.length;

            // Create storage for the combined array
            concatArray = (T[][]) Array.newInstance(array1.getClass().getComponentType(),
                                                    numRows1 + numRows2);

            // Copy the input arrays into the proper locations in the combined array
            System.arraycopy(array1, 0, concatArray, 0, numRows1);
            System.arraycopy(array2, 0, concatArray, numRows1, numRows2);
        }

        return concatArray;
    }

    /**********************************************************************************************
     * Convert an object array to a string array
     *
     * @param asObject
     *            single-dimensional object array to convert
     *
     * @return Single-dimensional array of string values
     *********************************************************************************************/
    protected static String[] convertObjectToString(Object[] asObject)
    {
        String[] asString = new String[0];

        // Check if there is data in the supplied array
        if (asObject.length != 0)
        {
            asString = new String[asObject.length];

            // Step through each row of the array
            for (int row = 0; row < asObject.length; row++)
            {
                // Store the object as a string
                asString[row] = asObject[row].toString();
            }
        }

        return asString;
    }

    /**********************************************************************************************
     * Convert an object array to a string array. The number of columns in the input array do not
     * have to have the same number of columns
     *
     * @param asObject
     *            two-dimensional object array to convert
     *
     * @return Two-dimensional array of string values
     *********************************************************************************************/
    protected static String[][] convertObjectToString(Object[][] asObject)
    {
        String[][] asString = new String[0][0];

        // Check if there is data in the supplied array
        if (asObject.length != 0)
        {
            asString = new String[asObject.length][];

            // Step through each row of the array
            for (int row = 0; row < asObject.length; row++)
            {
                String[] rowAsString = new String[asObject[row].length];

                // Step through each column of the array
                for (int column = 0; column < asObject[row].length; column++)
                {
                    // Store the object as a string
                    rowAsString[column] = asObject[row][column].toString();
                }

                // Add the converted row to the array
                asString[row] = rowAsString;
            }
        }

        return asString;
    }

    /**********************************************************************************************
     * Remove trailing characters from the end of a string
     *
     * @param text
     *            string from which to remove the trailing characters
     *
     * @param trailingText
     *            trailing characters to remove
     *
     * @return Input string minus the trailing characters (if present)
     *********************************************************************************************/
    protected static String removeTrailer(String text, String trailingText)
    {
        // Check if the string ends with the trailing characters
        if (text.endsWith(trailingText))
        {
            // Remove the trailing characters from the string
            text = text.substring(0, text.lastIndexOf(trailingText));
        }

        return text;
    }

    /**********************************************************************************************
     * Remove trailing characters from the end of a StringBuilder string
     *
     * @param text
     *            StringBuilder string from which to remove the trailing characters
     *
     * @param trailingText
     *            trailing characters to remove
     *
     * @return Input string minus the trailing characters (if present)
     *********************************************************************************************/
    protected static StringBuilder removeTrailer(StringBuilder text, String trailingText)
    {
        // Get the length of the string and the index where the trailing text should start
        int textLength = text.length();
        int index = textLength - trailingText.length();

        // Check if the trailing characters end the string
        if (text.lastIndexOf(trailingText) == index)
        {
            // Remove the trailing characters from the string
            text.replace(index, textLength, "");
        }

        return text;
    }

    /**********************************************************************************************
     * Remove any leading and trailing quotes from a quoted string, and replace pairs of double
     * quotes with one double quote
     *
     * @param text
     *            string from which to remove the excess quotes
     *
     * @return Input string minus any excess quotes
     *********************************************************************************************/
    protected static String removeExcessQuotes(String text)
    {
        return text.replaceAll("^\"|\"$", "").replaceAll("\"\"", "\"");
    }

    /**********************************************************************************************
     * Convert the array into a single string and remove the leading and trailing brackets
     *
     * @param array
     *            array of strings
     *
     * @return Single string containing the strings from the array, separated by a comma and a
     *         space
     *********************************************************************************************/
    protected static String convertArrayToString(Object[] array)
    {
        return Arrays.toString(array).replaceAll("^\\[|\\]$", "");
    }

    /**********************************************************************************************
     * Replace any HTML break tags with spaces, remove the remaining HTML tag(s) from the supplied
     * text, and replace special character markers with the special character if recognized, else
     * with a blank
     *
     * @param text
     *            string from which to remove the HTML tags
     *
     * @return Input string with spaces replacing breaks, minus any HTML tag(s), and with special
     *         character markers replaced
     *********************************************************************************************/
    protected static String removeHTMLTags(String text)
    {
        return removeHTMLTags(text, false);
    }

    /**********************************************************************************************
     * Replace any HTML break tags with line feeds or spaces (depending on the input flag), remove
     * the remaining HTML tag(s) from the supplied text, and replace special character markers with
     * the special character if recognized, else with a blank
     *
     * @param text
     *            string from which to remove the HTML tags
     *
     * @param preserveBreaks
     *            true to replace breaks with line feeds; false to replace breaks with spaces
     *
     * @return Input string with line feeds or spaces replacing breaks, minus any HTML tag(s), and
     *         with special character markers replaced
     *********************************************************************************************/
    protected static String removeHTMLTags(String text, boolean preserveBreaks)
    {
        return text.replaceAll(HTMLTag.BREAK.getHTMLTag(),
                               (preserveBreaks
                                               ? "\n"
                                               : " "))
                   .replaceAll("<[^>]*>", "")
                   .replaceAll(HTMLTag.SPACE.getHTMLTag(), " ")
                   .replaceAll(HTMLTag.AMP.getHTMLTag(), "&")
                   .replaceAll(HTMLTag.GREAT.getHTMLTag(), ">")
                   .replaceAll(HTMLTag.LESS.getHTMLTag(), "<")
                   .replaceAll("&.+;", "");
    }

    /**********************************************************************************************
     * Add the HTML tags to the supplied text to change the font color to the one specified
     *
     * @param text
     *            string to which to apply the HTML tags
     *
     * @param color
     *            color to which to set the text (Color)
     *
     * @return Input string with the HTML tags to alter the color to the one supplied
     *********************************************************************************************/
    protected static String colorHTMLText(String text, Color color)
    {
        return String.format("<font color=\"#%06x\">" + text + "</font>",
                             (0xffffff & color.getRGB()));
    }

    /**********************************************************************************************
     * Escape any PostgreSQL reserved characters in the supplied string so that when used in a
     * regular expression the characters are recognized properly
     *
     * @param text
     *            string in which to escape any reserved characters
     *
     * @return String with any reserved PostgreSQL characters escaped so that they are recognized
     *         by a regular expression
     *********************************************************************************************/
    protected static String escapePostgreSQLReservedChars(String text)
    {
        return text.replaceAll("\\\\", "\\\\\\\\\\\\\\\\")
                   .replaceAll("\n", "\\\\\\\\n")
                   .replaceAll(POSTGRESQL_RESERVED_CHARS, "$1\\\\\\\\$2$3");
    }

    /**********************************************************************************************
     * Convert the supplied text string to HTML, replacing the HTML special characters in the text
     * string with the equivalent HTML tags
     *
     * @param inputText
     *            string to format for HTML
     *
     * @return Input string converted to HTML format
     *********************************************************************************************/
    protected static String convertToHTML(String inputText)
    {
        return convertToHTML(inputText, false);
    }

    /**********************************************************************************************
     * Convert the supplied text string to HTML, replacing the HTML special characters in the text
     * string with the equivalent HTML tags
     *
     * @param inputText
     *            string to format for HTML
     *
     * @param ignoreBreaks
     *            true to ignore replacing line feeds with breaks; false to replace line feeds with
     *            breaks
     *
     * @return Input string converted to HTML format
     *********************************************************************************************/
    protected static String convertToHTML(String inputText, boolean ignoreBreaks)
    {
        // Step through each HTML tag
        for (HTMLTag tagInfo : HTMLTag.values())
        {
            // Check if this isn't the line break tag, or if it is that line breaks are not to be
            // ignored
            if (tagInfo != HTMLTag.BREAK || !ignoreBreaks)
            {
                // Replace the reserved character in the input text string with its HTML tag
                // equivalent
                inputText = tagInfo.replaceReservedChar(inputText);
            }
        }

        // Prepend the HTML tag
        inputText = HTMLTag.HTML.getHTMLTag() + inputText;

        return inputText;
    }

    /**********************************************************************************************
     * Search a string list for the specified text string, ignoring case
     *
     * @param text
     *            text string to search for in the list
     *
     * @param list
     *            string list to search through for the specified text
     *
     * @return true if the specified text string is present in the supplied list (case insensitive)
     *********************************************************************************************/
    protected static boolean contains(String text, List<String> list)
    {
        boolean isContains = false;

        // Step through each item in the list
        for (String item : list)
        {
            // Check if the text matches the item, ignoring case
            if (text.equalsIgnoreCase(item))
            {
                // Set the flag indicating a match exists and stop searching
                isContains = true;
                break;
            }
        }

        return isContains;
    }

    /**********************************************************************************************
     * Insert line breaks into the supplied string so as to limit the maximum length of each line
     * to the value specified. If the supplied string is formatted for HTML then the HTML tags are
     * removed prior to determining the line breaks insertion position(s). Once the line breaks are
     * added the HTML tags are restored. A non-HTML input string is converted to HTML format,
     * including conversion of any special characters
     *
     * @param inputText
     *            string to format for wrapping
     *
     * @param maxLength
     *            maximum length, in characters, to wrap the text
     *
     * @return The input text, converted to HTML format (if not already), with line breaks inserted
     *         to limit the line length to the maximum specified; null if the input string is null
     *         or blank
     *********************************************************************************************/
    protected static String wrapText(String inputText, int maxLength)
    {
        String outputText = null;

        // Check if the input string is not null or blank
        if (inputText != null && !inputText.isEmpty())
        {
            // Create storage for the tags
            List<Tags> tags = new ArrayList<Tags>();

            // Check if the input text string is not formatted for HTML
            if (!inputText.startsWith(HTMLTag.HTML.getHTMLTag()))
            {
                // Convert the input text to HTML format
                inputText = convertToHTML(inputText, true);
            }

            // Initialize the index of the last tag found
            int lastIndex = inputText.length();

            boolean isDone = false;

            // Process the input string to locate, store, and remove the HTML tags from the input
            // string
            while (!isDone)
            {
                // Get the tag index nearest the point in the string where the previously found tag
                // is located
                int tagIndex = Math.max(inputText.lastIndexOf("<", lastIndex),
                                        inputText.lastIndexOf("&", lastIndex));

                // Check if a tag was located
                if (tagIndex != -1)
                {
                    switch (inputText.charAt(tagIndex))
                    {
                        case '<':
                            // Get the tag's termination index
                            int endIndex = inputText.indexOf(">", tagIndex);

                            // Check if the tag isn't properly terminated
                            if (endIndex == -1)
                            {
                                // Use the end of the input string to terminate the tag
                                endIndex = inputText.length() - 1;
                            }

                            // Get the tag's text
                            String tagText = inputText.substring(tagIndex, endIndex + 1);

                            // Set the last found tag's index to the beginning of the tag
                            lastIndex -= tagText.length();

                            // Check if the tag is not a line break. Line break tags are left in
                            // the 'cleaned' string in order to perform the maximum line length
                            // adjustment
                            if (!tagText.equals(HTMLTag.BREAK.getHTMLTag()))
                            {
                                // Add the tag to the storage list
                                tags.add(0, new Tags(tagIndex, tagText));

                                // Remove the tag from the input string
                                inputText = inputText.substring(0, tagIndex)
                                            + inputText.substring(tagIndex + tagText.length());
                            }

                            break;

                        case '&':
                            boolean isFound = false;

                            // Step through the HTML tags
                            for (HTMLTag tagInfo : HTMLTag.values())
                            {
                                // Check if the tag is a reserved character and that it matches the
                                // one in the input string
                                if (tagInfo.isReservedChar()
                                    && inputText.startsWith(tagInfo.getHTMLTag(), tagIndex))
                                {
                                    // Set the last found tag's index to the beginning of the
                                    // special character sequence
                                    lastIndex -= tagInfo.getHTMLTag().length();

                                    // Add the tag to the storage list
                                    tags.add(0, new Tags(tagIndex, tagInfo.getHTMLTag()));

                                    // Remove the tag from the input string, substituting a
                                    // character in place of the tag so that the line wrap accounts
                                    // for the character
                                    inputText = inputText.substring(0, tagIndex)
                                                + tagInfo.getSpecialCharPlaceholder()
                                                + inputText.substring(tagIndex
                                                                      + tagInfo.getHTMLTag().length());

                                    isFound = true;
                                    break;
                                }
                            }

                            // Check if this instance of the '&' isn't for a special character
                            if (!isFound)
                            {
                                // Add the tag to the storage list
                                tags.add(0, new Tags(tagIndex, "&"));

                                // Remove the tag from the input string, substituting a character
                                // in place of the tag so that the line wrap accounts for the
                                // character
                                inputText = inputText.substring(0, tagIndex)
                                            + "_"
                                            + inputText.substring(tagIndex + 1);
                            }

                            break;

                        default:
                            // Set the flag to exit the loop
                            isDone = true;
                            break;
                    }
                }
                // No tags were found
                else
                {
                    // Set the flag to exit the loop
                    isDone = true;
                }
            }

            // Store the input string, minus any HTML tags (except line breaks)
            outputText = inputText;

            int breakIndex = 0;
            int breakCount = 0;

            // Insert breaks into the 'cleaned' string to constrain the width of the displayed text
            // to the specified maximum. Continue to process the input string while it's length is
            // above the maximum
            while (inputText.length() > maxLength)
            {
                int padLength = 0;

                // Get the index of the last space or line break (whichever is greater) in the
                // input string within the maximum allowed length
                int index = Math.max(inputText.substring(0, maxLength).lastIndexOf(" "),
                                     inputText.substring(0, maxLength).lastIndexOf(HTMLTag.BREAK.getHTMLTag()));

                // Check if no space or break was found
                if (index == -1)
                {
                    // Set the breakpoint to the maximum length
                    index = maxLength;
                }
                // A space or break was found
                else
                {
                    // Set the pad length to account for the length of the space or break tag
                    // characters
                    padLength = inputText.charAt(index) == ' '
                                                               ? " ".length()
                                                               : HTMLTag.BREAK.getHTMLTag().length();
                }

                // Check if the portion of the string to be broken already contains a line break
                if (inputText.substring(0, index + padLength).contains(HTMLTag.BREAK.getHTMLTag()))
                {
                    // Adjust the breakpoint index to account for the portions of the input string
                    // that are removed so that the index is applicable to the original string
                    // (with HTML tags removed)
                    breakIndex += inputText.substring(0, index + padLength)
                                           .indexOf(HTMLTag.BREAK.getHTMLTag())
                                  + HTMLTag.BREAK.getHTMLTag().length();

                    // Remove the text up to the breakpoint
                    inputText = inputText.substring(inputText.indexOf(HTMLTag.BREAK.getHTMLTag())
                                                    + HTMLTag.BREAK.getHTMLTag().length());
                }
                // The portion of the string to be broken does not contain a line break
                else
                {
                    // Adjust the breakpoint index to account for the portions of the input string
                    // that are removed so that the index is applicable to the original string
                    // (with HTML tags removed)
                    breakIndex += index + padLength;

                    // Remove the portion of the input string prior to the new breakpoint and skip
                    // the space that the line break replaces, if applicable
                    inputText = inputText.substring(index + padLength);

                    // Insert an HTML line break in the tag list. The breaks inserted for line
                    // wrapping are stored in the order they occur, but prior to any other HTML
                    // tags
                    tags.add(breakCount, new Tags(breakIndex, HTMLTag.BREAK.getHTMLTag()));

                    // Keep track of the number of inserted line breaks
                    breakCount++;
                }
            }

            int tagIndexAdjust = 0;
            boolean isBreakDone = false;
            int skipSpaceAdjust = 0;

            // Insert the tags back into the string, including any new line breaks for text
            // wrapping. Step through each stored HTML tag
            for (Tags tag : tags)
            {
                // Initialize the special character adjustment value
                int scAdjust = 0;

                // Get the index into the string for this tag
                int tagIndex = tag.getIndex();

                // Check if this is the <html> tag (i.e., any line breaks added for wrapping have
                // already been inserted)
                if (tagIndex == 0)
                {
                    // Reset the string index adjust back to zero since the tags following will be
                    // inserted beginning with the start of the string. Set the flag indicating the
                    // added line breaks, if any, have been processed
                    tagIndexAdjust = 0;
                    isBreakDone = true;
                }

                int breakIndexAdjust = 0;
                breakCount = 0;

                // The tag indices must be adjusted to account for any line breaks inserted for
                // line wrapping. Step through the tags
                for (Tags breakTag : tags)
                {
                    // Check if this is the <html> tag; i.e., the line breaks have all been checked
                    if (breakTag.getIndex() == 0)
                    {
                        // Stop searching
                        break;
                    }

                    // Compare the index of the current tag to the break tag's index (adjusted to
                    // account for any tags that have been inserted into the output string) to
                    // determine if the tag appears in the string after the break tag, and
                    // increment the break tag counter
                    if (tagIndex > breakTag.getIndex() + (isBreakDone
                                                                      ? tagIndexAdjust
                                                                      : 0))
                    {
                        // Add the length of a line break tag to the break index adjustment value
                        breakIndexAdjust += HTMLTag.BREAK.getHTMLTag().length();
                        breakCount++;
                    }
                }

                // Adjust the tag's string index to account for any line break tags that were
                // inserted ahead of it in the string
                tagIndex += breakIndexAdjust;

                // Check if all of the added line breaks have not been processed
                if (!isBreakDone)
                {
                    // Set the special character adjustment to account for the space left in the
                    // input string that the line break replaces. If the break doesn't replace a
                    // space (since none was in the vicinity) then the adjustment is zero
                    scAdjust = outputText.charAt(tagIndex) == ' '
                                                                  ? 0
                                                                  : 0;
                }

                // Add the length of this tag to the running total
                tagIndexAdjust += tag.getTag().length();

                // Check if this is special character tag
                if (tag.getTag().charAt(0) == '&')
                {
                    // Check if the tag is for a space and the character to be replaced isn't a
                    // space. This occurs if a line break replaces an existing space, in which case
                    // the space tag is no longer applicable
                    if (tag.getTag().equals(HTMLTag.SPACE.getHTMLTag())
                        && outputText.charAt(tagIndex - skipSpaceAdjust) != ' ')
                    {
                        // Add the length of the skipped space tag to the skipped space adjustment
                        // and go to the next tag
                        skipSpaceAdjust += tag.getTag().length();
                        continue;
                    }

                    // Set the special character adjustment to account for the placeholder
                    // character in the string that the tag will replace, and decrement the tag
                    // index adjustment for the same reason
                    scAdjust = 1;
                    tagIndexAdjust--;
                }

                // Insert the tag into the output string at the indicated index
                outputText = outputText.substring(0, tagIndex - skipSpaceAdjust)
                             + tag.getTag()
                             + outputText.substring(tagIndex + scAdjust - skipSpaceAdjust,
                                                    outputText.length());
            }
        }

        return outputText;
    }

    /**********************************************************************************************
     * Highlight the data type portion of a structure table/variable path
     *
     * @param path
     *            structure/variable path
     *
     * @return Structure/variable path with the data type(s) highlighted
     *********************************************************************************************/
    protected static String highlightDataType(String path)
    {
        // Check if the path contains a child structure reference and isn't disabled or invalid
        if (path.contains(".") && !path.startsWith(FLAGGED))
        {
            // Create the tag using the data type highlight color
            String highlightOn = "<font color="
                                 + String.format("#%02x%02x%02x",
                                                 ModifiableColorInfo.DATA_TYPE.getColor().getRed(),
                                                 ModifiableColorInfo.DATA_TYPE.getColor().getGreen(),
                                                 ModifiableColorInfo.DATA_TYPE.getColor().getBlue())
                                 + ">";
            String highlightOff = "<font color=#000000>";

            // Check if the path doesn't already begin with the HTML marker
            if (!path.startsWith("<html>"))
            {
                // Add the HTML marker and replace any spaces with the HTML equivalent
                path = "<html>" + path.replaceAll(" ", HTMLTag.SPACE.getHTMLTag());
            }

            // Add HTML tags to highlight the data type portion and replace each line feed
            // character with a line break
            path = path.replaceAll("(<html>.*?,|,|<html>)(.*?)(\\..*?)", "$1"
                                                                         + highlightOn
                                                                         + "$2"
                                                                         + highlightOff
                                                                         + "$3")
                       .replaceAll("\\n", "<br>");
        }

        return path;
    }

    /**********************************************************************************************
     * Highlight any text matching the search text in the the specified text component
     *
     * @param component
     *            reference to the table cell renderer component
     *
     * @param text
     *            cell value
     *
     * @param hightlightColor
     *            color used for highlighting the matching text
     *
     * @param searchPattern
     *            search pattern; can be a regular expression (Pattern)
     *
     * @return true if the supplied text contains a match to the specified search pattern
     *********************************************************************************************/
    protected static boolean highlightSearchText(Component component,
                                                 String text,
                                                 Color hightlightColor,
                                                 Pattern searchPattern)
    {
        boolean hasHighlight = false;

        // Check if the search pattern exists
        if (searchPattern != null)
        {
            int adjust = 0;

            // Get the reference to the component's highlighter
            Highlighter highlighter = ((JTextComponent) component).getHighlighter();

            // Remove any existing highlighting from the text
            highlighter.removeAllHighlights();

            // Highlight matching search text instances. Create a highlighter painter
            DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(hightlightColor);

            // Check if the text is HTML tagged
            if (text.startsWith("<html>"))
            {
                // Remove the HTML tags and set the match index adjust
                text = CcddUtilities.removeHTMLTags(text);
                adjust = 1;
            }

            // Create the pattern matcher from the pattern
            Matcher matcher = searchPattern.matcher(text);

            // Check if there is a match in the cell value
            while (matcher.find())
            {
                try
                {
                    // Highlight the matching text. Adjust the highlight color to account for the
                    // cell selection highlighting so that the matching search text is easily
                    // readable
                    highlighter.addHighlight(matcher.start() + adjust,
                                             matcher.end() + adjust,
                                             painter);

                    // Set the flag to indicate the text contains a match
                    hasHighlight = true;
                }
                catch (BadLocationException ble)
                {
                    // Ignore highlighting failure
                }
            }
        }

        return hasHighlight;
    }

    /**********************************************************************************************
     * Display a dialog for a generic exception, showing the cause and the stack trace
     *
     * @param e
     *            exception reference
     *
     * @param parent
     *            GUI component over which to center the dialog
     *********************************************************************************************/
    protected static void displayException(Exception e, Component parent)
    {
        // Build the dialog message
        String message = "<html><b>An unanticipated error occurred; cause '</b>"
                         + e.getMessage()
                         + "<b>'<br><br>Error trace:</b><br>";

        // Step through each element in the stack trace
        for (StackTraceElement ste : e.getStackTrace())
        {
            // Check if the reference is to a CCDD class (i.e., skip references in the Java library
            // classes)
            if (ste.getClassName().startsWith("CCDD"))
            {
                // Add the trace information to the message
                message += "&#160;&#160;"
                           + ste.getClassName().replaceFirst("CCDD\\.", "").replaceFirst("\\$\\d*", "")
                           + ": "
                           + ste.getMethodName()
                           + "() line "
                           + ste.getLineNumber()
                           + "<br>";
            }
        }

        // Display a dialog showing the stack trace
        new CcddDialogHandler().showMessageDialog(parent,
                                                  message,
                                                  "CCDD Error",
                                                  JOptionPane.ERROR_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**********************************************************************************************
     * Update the list of remembered items with the specified string. This is used by the search
     * and server auto-completion lists, and by the lists of previously opened projects and tables
     *
     * @param text
     *            item to add to the list. The item is placed at the head of the list. The list
     *            size is constrained to the maximum number specified when the field was created
     *
     * @param itemList
     *            list containing the items
     *
     * @param maxItems
     *            maximum number of items allowed in the list
     *********************************************************************************************/
    protected static void updateRememberedItemList(String text,
                                                   List<String> itemList,
                                                   int maxItems)
    {
        // Check if this is a repeat of a previous text string
        if (itemList.contains(text))
        {
            // Remove the text string from its current position in the remembered strings list
            // so that it can be put at the head of the list
            itemList.remove(text);
        }
        // Check if the maximum number of remembered strings has been reached
        else if (itemList.size() >= maxItems)
        {
            // Remove the oldest text string from the list
            itemList.remove(itemList.size() - 1);
        }

        // Insert the latest text string at the beginning of the remembered strings list
        itemList.add(0, text);
    }

    /**********************************************************************************************
     * Get the list of remembered items as a single, delimited string. This is used by the search
     * and server auto-completion lists, and by the lists of previously opened projects and tables
     *
     * @param itemList
     *            list containing the items to combine into a single string
     *
     * @return String containing the items from which the text is extracted, separated by delimiter
     *         characters
     *********************************************************************************************/
    protected static String getRememberedItemListAsString(List<String> itemList)
    {
        String listString = "";

        // Step through the remembered strings
        for (String listItem : itemList)
        {
            // Append the item string and separator characters to the single string
            listString += listItem + STRING_LIST_TEXT_SEPARATOR;
        }

        // Remove the trailing separator characters
        return removeTrailer(listString, STRING_LIST_TEXT_SEPARATOR);
    }
}
