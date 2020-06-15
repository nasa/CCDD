package CCDD;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.commons.io.FilenameUtils;

import CCDD.CcddClassesComponent.FileEnvVar;

public final class CcddBackupName {
    public static final String FAILSAFE_BACKUP_NAME = "failsafe_backup_name";

    /**
     * Take the full path to the backup file and remove the file name portion.
     * Take the user selected file name and append this a the initial path.
     * Note: This is to handle cases where there are spaces entered into the
     * filename
     * 
     * @param selectedPaths
     *            Array containing file paths
     * @param chosenBackupFileName
     *            User selected backup file name
     * @return A reconstructed path with the expected backup filename at the
     *         expected location or null on error
     */
    public static final FileEnvVar reconstructBackupFilePath(
            FileEnvVar[] selectedPaths, String chosenBackupFileName) {
        if (selectedPaths == null)
            return null;

        if (selectedPaths[0] == null)
            return null;

        if (selectedPaths[0].getAbsolutePath().length() <= 0)
            return null;

        if(chosenBackupFileName == null)
            return null;

        if(chosenBackupFileName.isEmpty())
            return null;

        // Get the chosen file name and remove all whatespace chars
        String selectedFileName = chosenBackupFileName.replaceAll("\"", "");

        // Reconstruct the resultant full file name
        FileEnvVar chosenBackupPath = new FileEnvVar(FilenameUtils.getFullPath(selectedPaths[0].toString())
                + selectedFileName);

        return chosenBackupPath;
    }

    /**
     * Take in a string of type [ID]_[DATE-TIME-STAMP].[EXT]
     * -Note:[DATE-TIME-STAMP] is optional Strip _[DATE-TIME-STAMP].[EXT] and
     * return the [ID] Note: If an error in the chosenBackupFileName is
     * detected, a failsafe result will be returned. If a null string is
     * entered, an exception will be thrown
     * 
     * @param extension
     *            Extension type (typically .dbu)
     * @param dateTimeFormat
     *            Format of the datetime (HHMMSS etc..)
     * @param chosenBackupFileName
     *            User selected backup file name
     * @param isTimeDateStamped
     *            Is there an _[DATE-TIME-STAMP] field in the
     *            chosenBackupFileName
     * @return [ID]
     */
    public static String removeExtensionTimeStamp(String extension,
            String dateTimeFormat, String chosenBackupFileName,
            boolean isTimeDateStamped) {

        // Argument checking
        if (extension == null || dateTimeFormat == null
                || chosenBackupFileName == null) {
            throw new NullPointerException();
        }

        if (chosenBackupFileName.isEmpty()) {
            return FAILSAFE_BACKUP_NAME;
        }

        // Get the chosen file name
        String selectedFileName = chosenBackupFileName.replaceAll("\"", "");

        if (selectedFileName.isEmpty()) {
            return FAILSAFE_BACKUP_NAME;
        }

        // Remove the extension
        selectedFileName = selectedFileName.replaceFirst(extension, "");

        // If the timestamp was added, remove it
        if (isTimeDateStamped) {
            String exampleTimeStamp = "_"
                    + new SimpleDateFormat(dateTimeFormat).format(Calendar
                            .getInstance().getTime());

            int cutOffPoint = selectedFileName.length()
                    - exampleTimeStamp.length();

            // Check for length and return the selected file if required
            if (cutOffPoint < 0) {
                return selectedFileName;
            }

            selectedFileName = selectedFileName.substring(0, cutOffPoint);

        }
        return selectedFileName;
    }
}
