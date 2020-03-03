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

Version 2 redefines the behavior of command tables.  Command arguments are no longer defined as columns within a command table.  Instead, the command table has a column that is a reference to a structure table; this structure defines the command argument(s).  The version 2 user's guide is updated to provide further details.

When version 2 attempts to open a version 1.x.x version project database then a dialog appears asking to convert the project.  Unlike previous patches, this patch alters user-defined tables and table definitions, and creates new ones.  The argument columns in any command tables are replaced with the argument structure reference column, and the argument structure is created and populated using the original argument information.  Many of the command table script data access methods no longer exist, so existing scripts may need to be updated. Before this patch is applied to the version 1.x.x database a backup will be performed to ensure no data loss on the chance that something does not work as anticipated. 
