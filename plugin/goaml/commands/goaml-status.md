---
description: Check the FIU status of a submitted goAML report and explain it
argument-hint: "[report id]"
allowed-tools: mcp__goaml__goaml_get_report, mcp__goaml__goaml_get_fiu_status, mcp__goaml__goaml_list_reports
---

Report the FIU status of report `$ARGUMENTS`.

1. If no id is given, use `goaml_list_reports` to show the tenant's reports and ask which one.
2. Call `goaml_get_fiu_status` for the report id. This polls the FIU for the latest status.
3. Explain the result plainly:
   - **SUBMITTED** — received by the FIU, awaiting processing.
   - **ACCEPTED** — the FIU accepted the report; include the `reportKey`.
   - **REJECTED** — the FIU rejected it; show the `errors` and outline what to fix before resubmitting.
4. If the report has never been submitted (the tool reports no submission to poll), say so and point to
   `/goaml-submit`.
