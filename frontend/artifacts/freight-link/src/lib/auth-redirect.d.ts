export function getSafeReturnTo(value: unknown): string | undefined;
export function getLoginRedirect(returnTo: string): string;
export function getPostLoginDestination(
  search: string,
  role: string,
  hasPendingInvitation: boolean,
): string;
