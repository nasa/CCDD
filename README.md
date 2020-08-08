# CCDD
Core Flight System (CFS) Command and Data Dictionary (CCDD) utility

*** CCDD Version 1.5.37 ***

*** CCDD works with JAVA 7-13 ***

CCDD is a software tool for managing the command and telemetry data for CFS and CFS applications.  CCDD is written in Java™ and interacts with a PostgreSQL database, so it can be used on any operating system that supports the Java Runtime Environment (JRE) and PostgreSQL.  CCDD is released as open source software under the NASA Open Source Software Agreement, version 1.3, and is hosted on GitHub.

The CCDD application uses tables, similar to a spreadsheet, to display and allow manipulation of telemetry data structures, command information, and other data pertinent to a CFS project.  The data is stored in a PostgreSQL database for manipulation and data security.  The PostgreSQL database server can be run locally or centralized on a remote host for easier access by multiple users.  Data can be imported into the application from files in comma-separated values (CSV), JavaScript Object Notation (JSON), electronic data sheet (EDS), and extensible markup language (XML) telemetric and command exchange (XTCE) formats.  Data can be exported from the application to files in CSV, JSON, EDS, and XTCE formats.  The CCDD tables also allow simple cut and paste operations from the host operating system’s clipboard.  To make use of the project’s data, CCDD can interact with Java Virtual Machine (JVM)-based scripting languages via a set of supplied data access methods.  Using scripts, the user can translate the data stored in the CCDD’s database into output files.  Example scripts for creating common CFS related output files are provided in four of these scripting languages.  An embedded web server can be activated, allowing web-based application access to the data.

See the CCDD user's guide for details on set up and use.

## CCDD version 2 

*** Version 2.0.16 is now released (see below for details) ***

*** CCDD version 2 works with JAVA 7-13 ***

*** CCDD version 2 has changed the way that the json import/export works. You can now import and export entire databases. Check CCDDv2 users guide for more details ***

Version 2 redefines the behavior of command tables.  Command arguments are no defined as columns within a command table.  Instead, the command table has a column that is a reference to a structure table; this structure defines the command argument(s).  The version 2 user's guide is updated to provide further details.

When version 2 attempts to open a version 1.x.x version project database then a dialog appears asking to convert the project.  Unlike previous patches, this patch alters user-defined tables and table definitions, and creates new ones.  The argument columns in any command tables are replaced with the argument structure reference column, and the argument structure is created and populated using the original argument information.  Many of the command table script data access methods no longer exist, so existing scripts may need to be updated. Before this patch is applied to the version 1.x.x database a backup will be performed to ensure no data loss on the chance that something does not work as anticipated. 

*** Version 2.0.20 has been released ***
* Fixed an issue with the patch handler that caused it to fail to convert a database from version 1 to version 2 if any text fields within the database contained an apostrophe.
* Fixed an issue with the patch handler where the internal __orders table was not being updated when converting a v1 database to v2. This resulted in the database failing when the "verify" functionality was used and reporting that tables with table type "Command" had out of order columns.
* Fixed an issue with the patch handler related to the conversion of tables with table type "Command". If these tables contained an array they were not being properly expanded. This resulted in the database failing to "verify" due to missing table members.
* Fixed an issue with v2 where data fields for certain tables were being cleared. If a v2 database was opened and then closed all of the data fields for tables with table type "Command" were being deleted.
* By default the "Command" event filter checkbox is unchecked in order to increase performance.
* Added a new checkbox to the export dialog that allows the user to decide if they would like to clear the contents of the target directory before an export.
* psql 8.4 - 11 is now supported. You can now create a .dbu backup on a machine with psql 11 and restore it on a machine with psql 8.4 or vice versa.
* Restored EDS and XTCE import/export functionality to CCDDv2. Works just as it did in v1.
* CSV import/export functionality has been updated to match the new JSON import/export functionality. You can now export entire databases, including internal tables, to JSON or CSV files. The data can be placed in separate files, every table in the database will get its own file, or one large file. This is useful for tracking changes to the database as these files can be easily checked into git. This also allows the user to make small changes to these files then perform an import which will update the database as needed.
* When files are compared for differences during an import and EOL characters will not be ignored.

*** Version 2.0.21 has been released ***
The ccdd.build.xml file is included in the repo. I have added notes to section 4.1 of the users guide that describe how to rebuild the tool if needed.

Below is a brief description of what has changed from version 2.0.20 to 2.0.21.
* Users were getting numerous errors, including a JDBC error, due to a change to the build.xml file that did not work as expected. This has been corrected.
* The replace existing tables check box during import did not replace the table, but simply appended to it.
* When exporting a single table, via the table editor, an empty table_info.json file was created.
* The use existing fields checkbox would create empty rows for each duplicate row.
* When exporting a table from the table editor it did not append the file name as it used to which forced the user to type out the name of the file each time.
* When exporting a table from the table editor window most of the options are now disabled. Example, Export entire database makes no sense when only one table is being exported.
* When an export failed CCDD sometimes reported that it passed.
* When importing directly into a table that is open, via the table editor, all data fields were cleared.
* When exporting a table from the table editor, via JSON, the end of the file was missing a parenthesis and had a comma added.
* When importing data any booleans would be set to false regardless of their actual value
* When importing data any duplicate rows would cause empty rows to be added to the end of the table
* When importing data fields the currently open table would often look correct, but once the user attempted to store the data it would cause an error and fail
* XTCE and EDS files can not store data field info. When importing one of these two file types all data field info was lost if the user elected to overwrite or append new data. Added a new checkbox that allows the user to chose if they wish to keep all currently existing data fields.
* Arrays could not be properly imported and had numerous issues
* When importing into a table of type "Command" the "Command Argument" column was always set to false regardless of its value which caused errors as the column is not of type boolean.
* Fixed an error that was preventing 2d arrays from being imported
* Fixed an error that occurred when macros were used to define the size of an array.
* Fixed an issue where the patch handler was sometimes requesting that a database be patched even if it was already a v2 database. This only occurred when launching from the command line.
* When backing up a database the name of the database will now be the same as the selected file name.
* COPY AND PASTE FUNCTIONALITY IS NOT WORKING AS EXPECTED. IT CAN BE USED, BUT DOES NOT WORK AS WELL AS IT DID IN THE PAST. WILL BE ADDRESSED IN A FUTURE RELEASE.

*** Version 2.0.22 has been released ***

Below is a brief description of what has changed from version 2.0.21 to 2.0.22.
* CCDD can now detect and better report issues related to unexpected JSON formats during an import
* Addressed an issue related to the primary key of tables not being given the correct value which resulted in many databases failing the verification process.
* Addressed an issue that prevented the internal groups table from being updated during an import.
* Addressed an issue that was preventing users from creating new tables.
* Addressed an issue where macro changes that removed tables which were included in an active group were not updating the internal groups table.
* Duplicate macros will now be filtered druing a JSON or CSV import
* Addressed an issue where a user would end up with duplicated data if they imported a change to a member of a non-primitive array.
* Multiple performance optimizations that greatly increase the speed at which a database can be opened or verifived and the speed at which CCDD can process a change in very large macros.
* Addressed an issue where some tables could not be directly opened from the seach dialog.
* Addressed an issue where some databases could not be converted from v1 to v2.
