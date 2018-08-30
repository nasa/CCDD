/**
 * CFS Command and Data Dictionary scheduler input interface.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.List;

import javax.swing.JPanel;

import CCDD.CcddClassesDataTable.Variable;

/**************************************************************************************************
 * CFS Command and Data Dictionary scheduler input interface
 *************************************************************************************************/
public interface CcddSchedulerInputInterface
{
    /**********************************************************************************************
     * Get the index at which the specified variable should be inserted in the list of variables
     * provided
     *
     * @param variable
     *            variable for which to determine the insertion index
     *
     * @param variables
     *            list of variables into which the variable is to be inserted
     *
     * @return Index at which to insert the target variable; -1 if the provided list is empty and
     *         -2 if the variable is already in the list
     *********************************************************************************************/
    abstract int getVariableRelativeIndex(Variable variable, List<Variable> variables);

    /**********************************************************************************************
     * Get the size of the variable or link that is selected
     *
     * @param variables
     *            list of variables; null to use the currently selected variables
     *
     * @return Size in bytes of the variable or link
     *********************************************************************************************/
    abstract int getSelectedVariableSize(List<Variable> variables);

    /**********************************************************************************************
     * Get a list of variable/applications at the specified rate
     *
     * @param rate
     *            rate column name
     *
     * @return List of variable objects representing the variables/applications at the specified
     *         rate
     *********************************************************************************************/
    abstract List<Variable> getVariablesAtRate(String rate);

    /**********************************************************************************************
     * Get the selected variable(s) of the variable tree
     *
     * @return List containing the selected variable(s)
     *********************************************************************************************/
    abstract List<Variable> getSelectedVariable();

    /**********************************************************************************************
     * Get an array of the available rates
     *
     * @return Array of sample rates
     *********************************************************************************************/
    abstract String[] getAvailableRates();

    /**********************************************************************************************
     * Get the currently selected rate
     *
     * @return Currently selected rate
     *********************************************************************************************/
    abstract String getSelectedRate();

    /**********************************************************************************************
     * Add the specified variable(s) to the excluded variable list
     *
     * @param excludeVariable
     *            list containing the variable(s) to be excluded
     *********************************************************************************************/
    abstract void excludeVariable(List<String> excludeVariable);

    /**********************************************************************************************
     * Remove the variable(s) from the excluded variable list
     *
     * @param includeVariable
     *            list of variables to be removed from the excluded variable list
     *********************************************************************************************/
    abstract void includeVariable(List<String> includeVariable);

    /**********************************************************************************************
     * Update the tree to display variables at the given rate
     *
     * @param rate
     *            rate for filtering the variables
     *********************************************************************************************/
    abstract void updateVariableTree(String rate);

    /**********************************************************************************************
     * Get the tree panel
     *
     * @return Tree panel object
     *********************************************************************************************/
    abstract JPanel getInputPanel();
}
