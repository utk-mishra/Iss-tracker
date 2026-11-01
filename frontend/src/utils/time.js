// Times shown in the user's local time with UTC on hover/tooltip, via Intl.DateTimeFormat
// (PROMPT.md section 7). Never crashes on null/undefined instants.

const PLACEHOLDER = '\u2014';

const localFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const utcFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'long',
  timeZone: 'UTC',
});

export function formatLocalTime(isoInstant) {
  if (!isoInstant) {
    return PLACEHOLDER;
  }
  const date = new Date(isoInstant);
  if (Number.isNaN(date.getTime())) {
    return PLACEHOLDER;
  }
  return localFormatter.format(date);
}

export function formatUtcTooltip(isoInstant) {
  if (!isoInstant) {
    return '';
  }
  const date = new Date(isoInstant);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return `${utcFormatter.format(date)} UTC`;
}

export function minutesAgo(ageMs) {
  if (!ageMs || ageMs < 0) {
    return 0;
  }
  return Math.floor(ageMs / 60000);
}
