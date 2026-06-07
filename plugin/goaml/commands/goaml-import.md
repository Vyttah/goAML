---
description: Import a goAML XML or DPMSR CSV file as draft reports and review the per-row results
argument-hint: "[path to an .xml or .csv file]"
allowed-tools: mcp__goaml__goaml_whoami, mcp__goaml__goaml_import_xml, mcp__goaml__goaml_import_csv, mcp__goaml__goaml_list_imports, mcp__goaml__goaml_get_import, Read
---

Import the file at `$ARGUMENTS` into goAML as draft reports. Importing requires the ANALYST or MLRO role.

1. Call `goaml_whoami` to confirm the tenant and role.
2. Read the file. Decide the format by extension/content: `.xml` (goAML XML) or `.csv` (flat DPMSR CSV).
3. Call the matching tool with the filename and the file's text content:
   - goAML XML → `goaml_import_xml`
   - DPMSR CSV → `goaml_import_csv`
4. Summarise the returned job: total rows, succeeded, failed, and the overall status (COMPLETED / PARTIAL /
   FAILED). For every failed or INVALID row, list the row number, its entity_reference, and the errors in
   plain language so the user can fix the source and re-import.
5. These are **drafts** — nothing is submitted. Point the user to `/goaml-submit` for any report they want to
   file (MLRO, with confirmation).

If the whole file is rejected (unreadable, missing required CSV headers, or over the row limit), explain the
rejection and what to correct.
