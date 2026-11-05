export type HistorySectionState = "loading" | "error" | "empty" | "records";

export interface HistoryQueryPlan {
  driver: boolean;
  shipper: boolean;
  fleetOwner: boolean;
}

export interface HistoryPageMetadata {
  hasNext?: boolean;
  last?: boolean;
}

export interface RetryableHistoryQuery {
  isFetchNextPageError: boolean;
  hasNextPage: boolean;
  fetchNextPage: () => unknown;
  refetch: () => unknown;
}

export function getHistoryQueryPlan(role: string | undefined): HistoryQueryPlan;
export function getHistorySectionState<T>({
  items,
  isError,
  isFetching,
}: {
  items: T[] | undefined;
  isError: boolean;
  isFetching: boolean;
}): HistorySectionState;
export function historyPageHasNext(page: HistoryPageMetadata): boolean;
export function retryHistoryQuery(query: RetryableHistoryQuery): unknown;
