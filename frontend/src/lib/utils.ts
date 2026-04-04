import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function pdfUrl(runId: string, pdfFilename?: string): string {
  if (pdfFilename) return `/api/reports/${runId}/pdf/${pdfFilename}`;
  return `/api/reports/${runId}/pdf`;
}

/**
 * Format a run timestamp for display in the browser's local timezone.
 * Accepts either an ISO-8601 string (new) or the legacy yyyyMMdd-HHmmss
 * directory-name format. The backend generates these in UTC; the browser
 * converts to local time via toLocaleString().
 */
export function formatRunTimestamp(ts: string): string {
  let date: Date;

  const legacy = ts.match(/^(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})$/);
  if (legacy) {
    // Backend generates run IDs in UTC explicitly
    date = new Date(Date.UTC(
      +legacy[1], +legacy[2] - 1, +legacy[3],
      +legacy[4], +legacy[5], +legacy[6],
    ));
  } else {
    date = new Date(ts);
  }

  if (isNaN(date.getTime())) return ts;

  return date.toLocaleString('en-GB', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).replace(',', '');
}
