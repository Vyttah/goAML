-- A3 — optional client-supplied report metadata. The AML cockpit (and any direct caller) may attach an opaque
-- top-level `clientMetadata` JSON object on create (e.g. the LiveExShield-parity capture fields: deal date,
-- internal reference, executed-by, reason, estimated amount). It is persisted VERBATIM here, returned in the
-- report detail view, and NEVER marshalled into the goAML XML sent to the FIU — it is captured-not-filed
-- context, kept so the system-of-record holds everything the user supplied (the "no lost field" guardrail).
-- Nullable: existing reports and callers that send nothing keep client_metadata = NULL.
ALTER TABLE report ADD COLUMN client_metadata jsonb;
