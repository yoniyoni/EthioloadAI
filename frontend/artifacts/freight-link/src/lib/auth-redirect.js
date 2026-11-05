const RETURN_TO_ORIGIN = "https://freight-link.invalid";

export function getSafeReturnTo(value) {
  if (
    typeof value !== "string" ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    value.includes("\\") ||
    /[\u0000-\u001f]/.test(value)
  ) {
    return undefined;
  }

  try {
    const destination = new URL(value, RETURN_TO_ORIGIN);
    if (destination.origin !== RETURN_TO_ORIGIN) return undefined;
    return `${destination.pathname}${destination.search}${destination.hash}`;
  } catch {
    return undefined;
  }
}

export function getLoginRedirect(returnTo) {
  const destination = getSafeReturnTo(returnTo);
  return destination
    ? `/login?returnTo=${encodeURIComponent(destination)}`
    : "/login";
}

export function getPostLoginDestination(search, role, hasPendingInvitation) {
  const query = search.startsWith("?") ? search.slice(1) : search;
  const returnTo = getSafeReturnTo(new URLSearchParams(query).get("returnTo"));
  if (returnTo) return returnTo;
  if (hasPendingInvitation) return "/invitations/accept";

  return role === "admin" ? "/admin" : "/dashboard";
}
