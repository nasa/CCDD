/**
 * CFS Command and Data Dictionary mathematical expression handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.List;

/**************************************************************************************************
 * CFS Command and Data Dictionary mathematical expression handler class
 *************************************************************************************************/
public class CcddMathExpressionHandler
{
    /**********************************************************************************************
     * Mathematical expression nest level class
     *********************************************************************************************/
    private static class NestLevel
    {
        private Double dValue;
        private int sign;
        private char operator;

        /******************************************************************************************
         * Mathematical expression nest level class constructor
         *****************************************************************************************/
        protected NestLevel()
        {
            dValue = null;
            sign = 1;
            operator = '\0';
        }

        /******************************************************************************************
         * Get the value of this nest level
         *
         * @return Value of this nest level
         *****************************************************************************************/
        protected Double getValue()
        {
            return dValue;
        }

        /******************************************************************************************
         * Set the value of this nest level
         *
         * @param dValue
         *            value of this nest level
         *****************************************************************************************/
        protected void setValue(Double dValue)
        {
            this.dValue = dValue;
        }

        /******************************************************************************************
         * Get the operator for this nest level
         *
         * @return Operator token
         *****************************************************************************************/
        protected char getOperator()
        {
            return operator;
        }

        /******************************************************************************************
         * Set the operator for this nest level
         *
         * @param operator
         *            operator token
         *****************************************************************************************/
        protected void setOperator(char operator)
        {
            this.operator = operator;
        }

        /******************************************************************************************
         * Get the sign value (1 or -1) for this nest level
         *
         * @return Sign value (1 or -1)
         *****************************************************************************************/
        protected int getSign()
        {
            return sign;
        }

        /******************************************************************************************
         * Set the sign value (1 or -1) for this nest level
         *
         * @param sign
         *            sign value (1 or -1)
         *****************************************************************************************/
        protected void setSign(int sign)
        {
            this.sign = sign;
        }
    }

    /**********************************************************************************************
     * Evaluate the supplied text as a mathematical expression
     *
     * @param expression
     *            text to evaluate as a mathematical expression
     *
     * @return The result of the evaluated expression; null if the supplied text doesn't evaluate
     *         to a numeric value (not a mathematical expression or the syntax is in error)
     *********************************************************************************************/
    protected static Double evaluateExpression(String expression)
    {
        int levelIndex = 0;
        boolean isExpression = true;
        Double result = null;

        // Create a list to contain the operators and results for each nested portion of the
        // expression
        List<NestLevel> nestLevels = new ArrayList<NestLevel>();
        NestLevel nestLevel = new NestLevel();
        nestLevels.add(nestLevel);

        // Step through each character in the expression
        for (int index = 0; index < expression.length() && isExpression; index++)
        {
            switch (expression.charAt(index))
            {
                // Addition operator or positive value sign
                case '+':
                    // Check if the value for this level is set
                    if (nestLevel.getValue() != null)
                    {
                        // Set the operator
                        nestLevel.setOperator('+');
                    }

                    break;

                // Subtraction operator or negative value sign
                case '-':
                    // Check if the value and operator for this level are set
                    if (nestLevel.getValue() != null && nestLevel.getOperator() == '\0')
                    {
                        // Set the operator
                        nestLevel.setOperator('-');
                    }
                    // The value and operator for this level aren't set (this is a sign and not an
                    // operator)
                    else
                    {
                        // Set the sign
                        nestLevel.setSign(-1);
                    }

                    break;

                // Multiplication operator
                case '*':
                    // Check if the value for this level is set
                    if (nestLevel.getValue() != null)
                    {
                        nestLevel.setOperator('*');
                    }
                    // The level doesn't have a value
                    else
                    {
                        // Set the flag to indicate that the supplied text isn't an expression (or
                        // has a syntax error)
                        isExpression = false;
                    }

                    break;

                // Division operator
                case '/':
                    // Check if the value for this level is set
                    if (nestLevel.getValue() != null)
                    {
                        nestLevel.setOperator('/');
                    }
                    // The level doesn't have a value
                    else
                    {
                        // Set the flag to indicate that the supplied text isn't an expression (or
                        // has a syntax error)
                        isExpression = false;
                    }

                    break;

                // Start of a nest level operator
                case '(':
                    // Create a new nest level
                    nestLevel = new NestLevel();
                    nestLevels.add(nestLevel);
                    levelIndex++;
                    break;

                // End of a nest level operator
                case ')':
                    // Check if the current nest level isn't the initial one
                    if (levelIndex != 0)
                    {
                        // Check if the nest level has a trailing operator or if the operation
                        // using the current nest level value and the previous level's running
                        // value fails
                        if (nestLevel.getOperator() != '\0'
                            || !performOperation(nestLevel.getValue(), nestLevels.get(levelIndex - 1)))
                        {
                            // Set the flag to indicate that the nest level failed to evaluate or
                            // has a trailing operator
                            isExpression = false;
                        }

                        // Remove the nest level
                        nestLevels.remove(levelIndex);
                        levelIndex--;
                        nestLevel = nestLevels.get(levelIndex);
                    }
                    // This is the initial nest level
                    else
                    {
                        // Set the flag to indicate that the supplied text isn't an expression (or
                        // has a syntax error)
                        isExpression = false;
                    }

                    break;

                // Bit-wise AND operator
                case '&':
                    // Check if the value for this level is set
                    if (nestLevel.getValue() != null)
                    {
                        nestLevel.setOperator('&');
                    }
                    // The level doesn't have a value
                    else
                    {
                        // Set the flag to indicate that the supplied text isn't an expression (or
                        // has a syntax error)
                        isExpression = false;
                    }

                    break;

                case '|':
                    // Bit-wise OR operator
                    // Check if the value for this level is set
                    if (nestLevel.getValue() != null)
                    {
                        nestLevel.setOperator('|');
                    }
                    // The level doesn't have a value
                    else
                    {
                        // Set the flag to indicate that the supplied text isn't an expression (or
                        // has a syntax error)
                        isExpression = false;
                    }

                    break;

                // Left bit shift operator
                case '<':
                    // Check if the next character is also a '<'
                    if (expression.length() > index + 1 && expression.charAt(index + 1) == '<')
                    {
                        // Check if the value for this level is set
                        if (nestLevel.getValue() != null)
                        {
                            nestLevel.setOperator('<');
                            index++;
                        }
                        // The level doesn't have a value
                        else
                        {
                            // Set the flag to indicate that the supplied text isn't an expression
                            // (or has a syntax error)
                            isExpression = false;
                        }
                    }
                    // The next character isn't a '<'
                    else
                    {
                        // Set the flag to indicate that the supplied text isn't an expression (or
                        // has a syntax error)
                        isExpression = false;
                    }

                    break;

                // Right bit shift operator
                case '>':
                    // Check if the next character is also a '>'
                    if (expression.length() > index + 1 && expression.charAt(index + 1) == '>')
                    {
                        // Check if the value for this level is set
                        if (nestLevel.getValue() != null)
                        {
                            nestLevel.setOperator('>');
                            index++;
                        }
                        // The level doesn't have a value
                        else
                        {
                            // Set the flag to indicate that the supplied text isn't an expression
                            // (or has a syntax error)
                            isExpression = false;
                        }
                    }
                    // The next character isn't a '>'
                    else
                    {
                        // Set the flag to indicate that the supplied text isn't an expression (or
                        // has a syntax error)
                        isExpression = false;
                    }

                    break;

                // Space character
                case ' ':
                    break;

                // Numeral or decimal point character
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case '.':
                    // Extract the numeric value (integer or floating point) from the text
                    // beginning at the current text index
                    String sValue = expression.substring(index).replaceFirst("([0-9\\.]+).*", "$1");

                    // Perform the operation using the current value and the nest level's running
                    // value. Set the flag if the evaluation fails
                    isExpression = performOperation(Double.valueOf(sValue), nestLevel);

                    // Update the text index to skip the numerals and decimal point encompassed by
                    // the numeric value
                    index += sValue.length() - 1;
                    break;

                // Non-mathematical expression character
                default:
                    // Set the flag to indicate that the supplied text isn't an expression (or has
                    // a syntax error)
                    isExpression = false;
                    break;
            }
        }

        // Check if the text is a mathematical expression, there is no unclosed nest level, and
        // there is no trailing operator
        if (isExpression && levelIndex == 0 && nestLevel.getOperator() == '\0')
        {
            // Get the expression result
            result = nestLevel.getValue();
        }

        return result;
    }

    /**********************************************************************************************
     * Perform the operation (# operator #), as specified by the supplied nest level's operator,
     * using the supplied value and the nest level's running value
     *
     * @param dValue
     *            first value
     *
     * @param nestLevel
     *            nest level that determines the second value and the operator
     *
     * @return true if the operation is valid; false if an error occurs (divide by zero condition
     *         exists)
     *********************************************************************************************/
    private static boolean performOperation(Double dValue, NestLevel nestLevel)
    {
        boolean isValid = true;

        // Check if the sign is negative for the nest level
        if (nestLevel.getSign() == -1)
        {
            // Negate the value and reset the sign
            dValue = -dValue;
            nestLevel.setSign(1);
        }

        // Check if the nest level doesn't have a value
        if (nestLevel.getValue() == null)
        {
            // Set the nest level's value to the supplied value
            nestLevel.setValue(dValue);
        }
        // The nest level has a value
        else
        {
            switch (nestLevel.getOperator())
            {
                // Addition operator
                case '+':
                    nestLevel.setValue(nestLevel.getValue() + dValue);
                    break;

                // Subtraction operator
                case '-':
                    nestLevel.setValue(nestLevel.getValue() - dValue);
                    break;

                // Multiplication operator
                case '*':
                    nestLevel.setValue(nestLevel.getValue() * dValue);
                    break;

                // Division operator
                case '/':
                    // Check if the supplied value greater than zero (including a tolerance value)
                    if (Math.abs(dValue) > 0.00000001)
                    {
                        nestLevel.setValue(nestLevel.getValue() / dValue);
                    }
                    // The supplied value is effectively zero, which would result in a divide by
                    // zero
                    else
                    {
                        // Set the flag to indicate the operation failed
                        isValid = false;
                    }

                    break;

                // Bit-wise AND operator
                case '&':
                    nestLevel.setValue(Double.parseDouble(String.valueOf((nestLevel.getValue().longValue() & dValue.longValue()))));
                    break;

                // Bit-wise OR operator
                case '|':
                    nestLevel.setValue(Double.parseDouble(String.valueOf((nestLevel.getValue().longValue() | dValue.longValue()))));
                    break;

                // Left bit shift operator
                case '<':
                    // Check if the right operand is non-negative
                    if (dValue >= 0)
                    {
                        nestLevel.setValue(Double.parseDouble(String.valueOf((nestLevel.getValue().longValue() << dValue.longValue()))));
                    }
                    // The right operand is negative; the operation is undefined
                    else
                    {
                        // Set the flag to indicate the operation failed
                        isValid = false;
                    }

                    break;

                // Right bit shift operator
                case '>':
                    // Check if the right operand is non-negative
                    if (dValue >= 0)
                    {
                        // Set the result to 0 if the right operand is negative
                        nestLevel.setValue(Double.parseDouble(String.valueOf((nestLevel.getValue().longValue() >> dValue.longValue()))));
                    }
                    // The right operand is negative; the operation is undefined
                    else
                    {
                        // Set the flag to indicate the operation failed
                        isValid = false;
                    }

                    break;

                // No operator
                default:
                    break;
            }
        }

        // Reset the nest level's operator
        nestLevel.setOperator('\0');

        return isValid;
    }
}
