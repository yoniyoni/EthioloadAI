import assert from "node:assert/strict";
import test from "node:test";
import {
  getHistoryQueryPlan,
  getHistorySectionState,
  historyPageHasNext,
  retryHistoryQuery,
} from "../src/pages/history-model.js";

test("classifies successful populated and empty history responses", () => {
  assert.equal(getHistorySectionState({
    items: [{ id: 1 }],
    isError: false,
    isFetching: false,
  }), "records");
  assert.equal(getHistorySectionState({
    items: [],
    isError: false,
    isFetching: false,
  }), "empty");
});

test("reports a failed section independently from a successful section", () => {
  const bookings = getHistorySectionState({
    items: undefined,
    isError: true,
    isFetching: false,
  });
  const trips = getHistorySectionState({
    items: [{ id: 2 }],
    isError: false,
    isFetching: false,
  });
  assert.equal(bookings, "error");
  assert.equal(trips, "records");

  const successfulBookings = getHistorySectionState({
    items: [],
    isError: false,
    isFetching: false,
  });
  const failedTrips = getHistorySectionState({
    items: undefined,
    isError: true,
    isFetching: false,
  });
  assert.equal(successfulBookings, "empty");
  assert.equal(failedTrips, "error");
});

test("shows loading instead of a stale error while an initial retry is in flight", () => {
  assert.equal(getHistorySectionState({
    items: undefined,
    isError: true,
    isFetching: true,
  }), "loading");
});

test("uses backend pagination metadata from booking and trip page responses", () => {
  assert.equal(historyPageHasNext({ hasNext: true, last: true }), true);
  assert.equal(historyPageHasNext({ last: false }), true);
  assert.equal(historyPageHasNext({ last: true }), false);
  assert.equal(historyPageHasNext({}), false);
});

test("retries only the selected history query and resumes a failed next page", () => {
  const calls = [];
  const bookings = {
    isFetchNextPageError: false,
    hasNextPage: false,
    fetchNextPage: () => calls.push("bookings-next"),
    refetch: () => calls.push("bookings-refetch"),
  };
  const trips = {
    isFetchNextPageError: true,
    hasNextPage: true,
    fetchNextPage: () => calls.push("trips-next"),
    refetch: () => calls.push("trips-refetch"),
  };

  retryHistoryQuery(bookings);
  assert.deepEqual(calls, ["bookings-refetch"]);
  retryHistoryQuery(trips);
  assert.deepEqual(calls, ["bookings-refetch", "trips-next"]);
});

test("keeps booking, trip, bid, and shipper-history requests role-specific", () => {
  assert.deepEqual(getHistoryQueryPlan("fleet_owner"), {
    driver: false,
    shipper: false,
    fleetOwner: true,
  });
  assert.deepEqual(getHistoryQueryPlan("driver"), {
    driver: true,
    shipper: false,
    fleetOwner: false,
  });
  assert.deepEqual(getHistoryQueryPlan("shipper"), {
    driver: false,
    shipper: true,
    fleetOwner: false,
  });
  assert.deepEqual(getHistoryQueryPlan(undefined), {
    driver: false,
    shipper: false,
    fleetOwner: false,
  });
});
