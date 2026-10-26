import { defineConfig } from "orval";

// Safaricom's callback endpoints are server-to-server, so the console never calls them.
const input = {
  target: "./openapi.json",
  filters: { mode: "exclude" as const, tags: [/callback/i] },
};

export default defineConfig({
  mpesa: {
    input,
    output: {
      mode: "tags-split",
      target: "src/api/generated/endpoints",
      schemas: "src/api/generated/model",
      client: "react-query",
      httpClient: "fetch",
      clean: true,
      override: {
        mutator: { path: "src/api/fetcher.ts", name: "mpesaFetch" },
      },
    },
  },
  mpesaZod: {
    input,
    output: {
      mode: "tags-split",
      target: "src/api/generated/zod",
      client: "zod",
      fileExtension: ".zod.ts",
      clean: true,
    },
  },
});
