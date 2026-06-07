---
description: Validate a goAML DPMSR report and explain any failures in plain language
argument-hint: "[report id, entity_reference, or pasted/described DPMSR details]"
allowed-tools: mcp__goaml__goaml_whoami, mcp__goaml__goaml_validate_dpmsr, mcp__goaml__goaml_describe_report_type, mcp__goaml__goaml_list_lookups, mcp__goaml__goaml_list_reports, mcp__goaml__goaml_get_report
---

Validate a DPMSR report and explain the result clearly. Target: $ARGUMENTS

Do this:

1. Call `goaml_whoami` to confirm the connected tenant and role (you need ANALYST or MLRO to validate).
2. Determine what to validate from the argument:
   - If it looks like an existing report (a UUID or a known entity_reference), use `goaml_get_report` /
     `goaml_list_reports` to locate it and summarise its stored status. (Re-running validation needs the
     report's input fields — if the user wants a fresh check, gather the DPMSR details as below.)
   - Otherwise treat the argument as the DPMSR details to validate. If anything required is missing
     (entityReference, submissionDate, reportingPerson, at least one party, at least one goods line), ask
     for it. Use `goaml_describe_report_type DPMSR` and `goaml_list_lookups` if you need to confirm shape
     or valid codes.
3. Call `goaml_validate_dpmsr` with the assembled request.
4. Report the verdict:
   - If **VALID**, say so and note any WARNINGs (e.g. non-AED goods that couldn't be summed for the AED
     55,000 threshold).
   - If **INVALID**, list each ERROR in plain language — what's wrong, where (the `path`), and how to fix it
     (e.g. "the total value is below the AED 55,000 reporting threshold", "currency must be AED",
     "entity_reference is required and must be unique"). Offer to apply fixes and re-validate.

Do not create or submit anything in this command — validation only.
