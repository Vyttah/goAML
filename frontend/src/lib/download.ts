/**
 * Browser download helpers. The backend serves bytes (report XML, attachments) through the API with the
 * bearer token attached by axios; these turn the in-memory response into a file the browser saves.
 */

/** Trigger a browser "save as" of a Blob under the given filename. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/** Download a string as a file (defaults to plain text). */
export function downloadText(text: string, filename: string, mime = 'text/plain'): void {
  downloadBlob(new Blob([text], { type: mime }), filename);
}
