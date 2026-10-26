import type { Metadata } from "next";
import { Suspense } from "react";
import { ProductsScreen } from "@/components/products/products-screen";
import ProductsLoading from "./loading";

export const metadata: Metadata = { title: "Products" };

// The deliveries table reads its page and filter from the URL, so the screen renders inside Suspense.
export default function ProductsPage() {
  return (
    <Suspense fallback={<ProductsLoading />}>
      <ProductsScreen />
    </Suspense>
  );
}
