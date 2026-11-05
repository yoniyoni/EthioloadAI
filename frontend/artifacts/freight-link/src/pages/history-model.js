export function getHistoryQueryPlan(role) {
  return {
    driver: role === "driver",
    shipper: role === "shipper",
    fleetOwner: role === "fleet_owner",
  };
}

export function getHistorySectionState({ items, isError, isFetching }) {
  if (items === undefined) {
    if (isFetching) return "loading";
    if (isError) return "error";
    return "loading";
  }
  return items.length === 0 ? "empty" : "records";
}

export function historyPageHasNext(page) {
  if (typeof page.hasNext === "boolean") return page.hasNext;
  if (typeof page.last === "boolean") return !page.last;
  return false;
}

export function retryHistoryQuery(query) {
  if (query.isFetchNextPageError && query.hasNextPage) {
    return query.fetchNextPage();
  }
  return query.refetch();
}
