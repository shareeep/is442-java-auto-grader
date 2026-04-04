import { describe, it, expect } from 'vitest';
import { formatRunTimestamp } from './utils';

// Vitest is run with TZ=Asia/Singapore (UTC+8).
// Legacy run IDs are UTC digits from the backend; the function converts them to local (SGT).

describe('formatRunTimestamp', () => {
  describe('legacy run ID format (yyyyMMdd-HHmmss, generated in UTC by backend)', () => {
    it('displays 06:00 UTC as 14:00 SGT on the same date', () => {
      expect(formatRunTimestamp('20260403-060000')).toBe('03/04/2026 14:00');
    });

    it('displays 00:00 UTC as 08:00 SGT on the same date', () => {
      expect(formatRunTimestamp('20260403-000000')).toBe('03/04/2026 08:00');
    });

    it('displays 23:00 UTC as 07:00 SGT the following date', () => {
      expect(formatRunTimestamp('20260403-230000')).toBe('04/04/2026 07:00');
    });

    it('truncates seconds — displays only hours and minutes', () => {
      // 12:34:56 UTC → 20:34 SGT (seconds dropped)
      expect(formatRunTimestamp('20260101-123456')).toBe('01/01/2026 20:34');
    });
  });

  describe('ISO-8601 format', () => {
    it('displays a Z-suffixed ISO string converted to SGT', () => {
      // Same moment as 20260403-060000 above
      expect(formatRunTimestamp('2026-04-03T06:00:00.000Z')).toBe('03/04/2026 14:00');
    });
  });

  describe('invalid input', () => {
    it('returns the original string when input is not a recognised timestamp', () => {
      expect(formatRunTimestamp('not-a-timestamp')).toBe('not-a-timestamp');
    });

    it('returns an empty string when given an empty string', () => {
      expect(formatRunTimestamp('')).toBe('');
    });
  });
});
