---
description: Interview the user to build a valid goAML DPMSR report, validate it, preview the XML, and optionally save a draft
argument-hint: "[optional: a short description of the dealing to report]"
allowed-tools: mcp__goaml__goaml_whoami, mcp__goaml__goaml_describe_report_type, mcp__goaml__goaml_list_jurisdictions, mcp__goaml__goaml_list_lookup_sets, mcp__goaml__goaml_list_lookups, mcp__goaml__goaml_validate_dpmsr, mcp__goaml__goaml_preview_dpmsr_xml, mcp__goaml__goaml_create_dpmsr
---

Guide the user to build a correct DPMSR (UAE precious-metals dealer report). Context: $ARGUMENTS

Follow this workflow and keep the user in control:

1. **Confirm context.** Call `goaml_whoami` (need ANALYST or MLRO). Call `goaml_describe_report_type DPMSR`
   to confirm the shape (activity-based: parties + goods, no transactions) and the AED 55,000 threshold.

2. **Gather the fields**, asking only for what's missing:
   - `entityReference` — a unique reference for this filing (idempotency key).
   - `submissionDate` — ISO-8601, default to now if the user agrees.
   - `reportingPerson` — the dealer's reporting officer (first/last name).
   - `parties` — at least one. Each is **either** a company (`entity`: name, incorporation country, …)
     **or** a person (first/last name, …) — not both.
   - `goods` — at least one. For each: `itemType` (e.g. GOLD), `estimatedValue`, `currencyCode` (default
     AED), and any make/description/size. Use `goaml_list_lookups` to confirm currency/country codes.
   - Optional: `reason`, `action`, `indicators`, `location`.

3. **Validate.** Call `goaml_validate_dpmsr`. If INVALID, explain each ERROR plainly, fix with the user, and
   re-validate until VALID. Surface WARNINGs (e.g. non-AED goods that can't be auto-summed for the threshold).

4. **Preview.** Call `goaml_preview_dpmsr_xml` and show the user the exact goAML XML that would be submitted.

5. **Save a draft only if the user confirms.** Call `goaml_create_dpmsr` and report back the new report id +
   status. Make clear this **saves a draft — it does not submit to the FIU**. Submission is a separate,
   explicit, MLRO-confirmed step; never submit from this command.
