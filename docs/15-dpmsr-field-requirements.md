# 15 — DPMSR field requirements (mandatory vs optional)

> **What this is:** the authoritative mandatory/optional map for every field in a DPMSR report, for the
> **frontend "required" markers** and any caller building a report. It is derived **directly from the goAML
> XSD** (`src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd`) — i.e. exactly what our `XsdSchemaValidator`
> gate enforces.
>
> **Keep this current:** when the vendored XSD version changes, **re-run the extraction recipe** at the
> bottom and update the tables. Treat the XSD as the source of truth; this doc is a readable projection of it.

## Two caveats before you mark a field "required"

1. **XSD-level only.** This reflects the schema's `minOccurs` — what our gate enforces today. The UAE FIU's
   **Business Rule Requirements (BRRs)** typically mandate *more* than the schema for DPMSR specifically
   (e.g. ID-document rules). We don't encode the BRRs yet (see `docs/05-engine.md` — "No Emirates-ID/passport
   rules yet"). Mark these as required now; tighten when the BRRs are confirmed.
2. **Where we're already stricter than the XSD** — keep these required even though the schema says optional:
   - **`reporting_person`** — schema makes the whole block optional (`reporting_person | reporting_user_code`
     is `minOccurs=0`); we require it (it's the MLRO).
   - **goods `estimated_value`** — schema `minOccurs=0`; our `DpmsrCreateRequest`/payload treat it as required
     (`@NotNull`).

## Legend

🔴 required · ⚪ optional · 🔒 server-applied (don't render as input) · 🔗 optional **pair** (provide both or
neither) · ◆ **choice** (pick exactly one)

---

## Report header

| Field | | Note |
|---|---|---|
| `entity_reference` | 🔴 | user-entered |
| `submission_date` | 🔴 | ◆ schema allows `report_date` instead; we use `submission_date` |
| `report_indicators` (≥1 `indicator`) | 🔴 | |
| `reporting_person` | 🔴 | *XSD-optional but we require it (the MLRO)* |
| `rentity_id`, `submission_code`, `report_code`, `currency_code_local` | 🔒 | auto-applied (your entity id / `E` / `DPMSR` / `AED`) |
| `rentity_branch`, `fiu_ref_number`, `reason`, `action`, `location` | ⚪ | |

## Reporting person (`t_person_registration_in_report`)

- 🔴 `first_name`, `last_name`
- 🔗 `passport_number` + `passport_country`
- ⚪ everything else (`gender`, `birthdate`, `ssn`, `id_number`, `nationality1`, `residence`, phone, address,
  `email`, `occupation`, …)

## Goods / item (`t_trans_item`)

- 🔴 `item_type`
- 🔴 `estimated_value` — *XSD-optional, but **we enforce it**; keep required*
- ⚪ `item_make`, `description`, `status_code`, `status_comments`, `disposed_value`, `currency_code`, `size`,
  `size_uom`, `registration_date`, `registration_number`, `identification_number`, `comments`,
  `presently_registered_to`, `previously_registered_to`, `address`

## Party wrapper (`report_party_type`)

- 🔴 `reason`; ◆ exactly one subject — for DPMSR: **`entity`** or **`person` (my client)**
- ⚪ `role`, `country`, `is_suspected`, `significance`, `comments`

## Entity party (`t_entity`)

- 🔴 `name` — **the only hard-required field**
- ⚪ `commercial_name`, `incorporation_legal_form`, `incorporation_number`, `incorporation_state`,
  `incorporation_country_code`, `incorporation_date`, `business`, phones, addresses, `tax_number`,
  `tax_reg_number`, `entity_identifications`, `url`/`email`, `sanctions`, `comments`, …
- 🔗 `entity_status` + `entity_status_date`
- ◆ director (optional): `director_id` **or** `related_persons`

## Director (`director_id` → `t_person` + `role`)

- 🔴 `first_name`, `last_name`
- 🔗 `passport_number` + `passport_country`
- ⚪ everything else; `role` optional

## ⚠️ Person *party* (`t_person_my_client`) — much heavier than the reporting person

- 🔴 `gender`, `first_name`, `last_name`, `birthdate`, `id_number`, `nationality1`, `residence`,
  `tax_reg_number`
- 🔴 `phones` — the wrapper element must be present (the inner `phone` is optional)
- 🔴 ◆ **identification** — at least one mandatory (inline `identification` or an `identifications` wrapper)
- 🔗 `passport_number` + `passport_country`
- ⚪ `title`, `middle_name`, `ssn`, addresses, `email`, `occupation`, `country_of_birth`, …

## Identification (`t_person_identification`) — when present

- 🔴 `type`, `number`, `issue_date`, `expiry_date`, `issue_country` · ⚪ `issued_by`, `comments`

## Address (`t_address`) — optional block; if added:

- 🔴 `address_type`, `address`, `city`, `country_code`, `state`
- ⚪ `house_number`, `apartment_number`, `additional_address_line1/2`, `town`, `zip`, `geo_location`, `comments`

## Phone (`t_phone`) — optional block; if added:

- 🔴 `tph_contact_type`, `tph_communication_type`, `tph_number` · ⚪ `tph_country_prefix`, `tph_extension`,
  `comments`

---

## Frontend implication — person requiredness is **context-dependent**

The SPA uses one shared `PersonFields` component for all people, but the required set differs by role:

| Person context | Required |
|---|---|
| **Reporting person** (MLRO) | `first_name`, `last_name` |
| **Director** | `first_name`, `last_name` |
| **Person party** (the customer) | `gender`, `first_name`, `last_name`, `birthdate`, `id_number`, `nationality1`, `residence`, `tax_reg_number`, a phone block, **and** ≥1 full identification |

A **person-party** form should therefore enforce ~8 fields + an identification; the **reporting-person** and
**director** forms need only first/last name. (This is also why a screening profile can't auto-produce a
*valid* person-party — it rarely carries `tax_reg_number` + a full ID document — so it seeds an analyst-
completed draft.)

---

## How to keep this current (regeneration recipe)

When the vendored XSD changes (version bump under `src/main/resources/xsd/goaml/<version>/`), re-run this to
recompute the `minOccurs` tree, then update the tables above. The key subtlety the script handles: an element
is only **truly required** if it *and every ancestor `<xs:sequence>`/`<xs:choice>`* is required — an element
with `minOccurs=1` inside a `minOccurs=0` group is a 🔗 conditional, not 🔴.

```python
import xml.etree.ElementTree as ET
XSD = "src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd"; NS = "{http://www.w3.org/2001/XMLSchema}"
root = ET.parse(XSD).getroot()
ctypes = {c.get('name'): c for c in root.iter(f"{NS}complexType") if c.get('name')}
elems = {e.get('name'): e for e in root.findall(f"{NS}element")}
def opt(n): return n.get('minOccurs', '1') == '0'
def walk(node, d, anc, inch, out):
    for ch in node:
        t = ch.tag.replace(NS, '')
        if t in ('sequence', 'all'):
            if opt(ch): out.append("  "*d+"(optional group:)"); walk(ch, d+1, False, inch, out)
            else: walk(ch, d, anc, inch, out)
        elif t == 'choice':
            out.append("  "*d+"[CHOICE pick one%s]" % ("" if anc and not opt(ch) else " (opt)"))
            walk(ch, d+1, anc and not opt(ch), True, out)
        elif t in ('complexContent', 'simpleContent', 'extension'): walk(ch, d, anc, inch, out)
        elif t == 'element':
            n = ch.get('name') or ch.get('ref'); m = "·" if inch else ("R" if anc and not opt(ch) else "o")
            out.append("  "*d+f"{m} {n}")
            ict = ch.find(f"{NS}complexType")
            if ict is not None and d < 4: walk(ict, d+1, (anc and not opt(ch)) and not inch, False, out)
    return out
for t in ["t_person_registration_in_report", "t_trans_item", "t_entity", "t_person_my_client",
          "t_address", "t_phone", "t_person", "t_person_identification"]:
    print(f"\n===== {t} =====");  [print(l) for l in walk(ctypes[t], 0, True, False, [])]
# header/body: walk(elems['report'].find(f"{NS}complexType"), 0, True, False, [])
```

---

**Related:** [05 — Engine](05-engine.md) (the two contracts: `DpmsrReportPayload` full-fidelity vs the curated
`DpmsrCreateRequest`) · the full-schema-fidelity rationale: `.planning/plans/full-schema-fidelity.md`.
