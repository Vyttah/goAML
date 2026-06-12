import type { FormInstance } from 'antd';

/**
 * Map Zod issues (flat `path → message`, where `path` is dot-joined like `parties.0.person.firstName`)
 * onto the matching AntD form fields so the errors render inline next to the offending input instead
 * of only in a bottom summary. The summary alert is kept too (the caller still renders it).
 */

/** Turn a dot-joined Zod issue path into an AntD field NamePath; numeric segments become indices. */
export function zodPathToFieldName(path: string): (string | number)[] {
  return path
    .split('.')
    .filter((seg) => seg.length > 0)
    .map((seg) => (/^\d+$/.test(seg) ? Number(seg) : seg));
}

/**
 * Attach Zod issues to their form fields and scroll to the first one. Issues sharing a path are
 * merged into one field with multiple error lines. Paths that are empty (object-root issues) are
 * skipped — they have no field to attach to and stay in the summary alert. `scrollToField` is guarded
 * because jsdom doesn't implement `scrollIntoView`.
 */
export function applyZodIssuesToForm(
  form: FormInstance,
  issues: { path: string; message: string }[],
): void {
  if (issues.length === 0) return;
  const byField = new Map<string, { name: (string | number)[]; errors: string[] }>();
  for (const issue of issues) {
    if (!issue.path) continue;
    const existing = byField.get(issue.path);
    if (existing) existing.errors.push(issue.message);
    else byField.set(issue.path, { name: zodPathToFieldName(issue.path), errors: [issue.message] });
  }
  const fields = [...byField.values()];
  if (fields.length === 0) return;
  form.setFields(fields.map((f) => ({ name: f.name, errors: f.errors })));
  try {
    form.scrollToField(fields[0].name, { behavior: 'smooth', block: 'center' });
  } catch {
    // best-effort: the inline errors are already attached (jsdom lacks scrollIntoView).
  }
}
