# Modifications

## 2026-09-27

Sylvester Kaczmarek modified `CcddCSVHandler.retrieveCSVData` to compare
CSV section keys by value, including the four composite-section branches.
Regression tests cover copied and reader-created keys, supported section
boundaries, associated fields, and unchanged unsupported-key behavior.

This modification is derived from the original Core Flight System Command
and Data Dictionary software provided by NASA and is characterized as a
Modification under the NASA Open Source Agreement, version 1.3.
