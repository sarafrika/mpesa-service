type Response = { status: number; data: unknown };

/** The body of a 202 Accepted answer (balance query, sweep request); mpesaFetch throws on errors. */
export function acceptedData<T extends Response>(response: T | undefined) {
  return response?.data as Extract<T, { status: 202 }>["data"] | undefined;
}
