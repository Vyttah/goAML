---
description: Safely submit a goAML report to the UAE FIU — dry-run, human review, then an MLRO-confirmed irreversible send
argument-hint: "[report id]"
allowed-tools: mcp__goaml__goaml_whoami, mcp__goaml__goaml_get_report, mcp__goaml__goaml_submit_report, mcp__goaml__goaml_get_fiu_status
---

Submit report `$ARGUMENTS` to the UAE FIU **safely**. Filing is irreversible — follow every step; never skip
the dry run or the human confirmation.

1. **Confirm role.** Call `goaml_whoami`. Submitting requires the **MLRO** role. If the user is not MLRO,
   stop and explain that only an MLRO can file to the FIU.
2. **Check the report.** Call `goaml_get_report` for the id. Confirm it exists and its status is **VALID**.
   If it is not VALID, stop — it must be fixed and re-validated before it can be submitted.
3. **Dry run.** Call `goaml_submit_report` with the report id and `dryRun=true` (the default). Show the user
   the returned `xmlPreview` — this is the exact goAML XML that would be filed. Nothing has been sent.
4. **Get explicit human confirmation.** Ask the user to review the XML and confirm, in their own words, that
   they want to file this report with the regulator. Do **not** proceed on an implied yes.
5. **Real submission.** Only after explicit confirmation, call `goaml_submit_report` with `dryRun=false` and
   `confirm=true`. Report back the `reportKey`.
6. **Track.** Offer to check the outcome with `goaml_get_fiu_status` (or `/goaml-status`).

If the tool returns a rejection or transport failure, explain it plainly and the next step (fix & resubmit,
or retry a transient failure). Never retry a real submission in a loop.
