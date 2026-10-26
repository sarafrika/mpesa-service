"use client";

import { Plus } from "lucide-react";
import { useState } from "react";
import { SectionBoundary } from "@/components/error-boundary";
import { PageHeader } from "@/components/page-header";
import { RoleGate } from "@/components/role-gate";
import { Button } from "@/components/ui/button";
import { ConnectProductDialog } from "./connect-product-dialog";
import { ProductList } from "./product-list";
import { RecentDeliveries } from "./recent-deliveries";

/** Products & integrations: product cards, then every webhook delivery. */
export function ProductsScreen() {
  const [connecting, setConnecting] = useState(false);
  const connect = () => setConnecting(true);

  return (
    <>
      <PageHeader
        title="Products & integrations"
        description="Sarafrika products that move money through this gateway. Each is a Keycloak client limited to the paybills and operations granted here."
        actions={
          <RoleGate needs="mpesa-admin">
            <Button onClick={connect}>
              <Plus /> Connect a product
            </Button>
          </RoleGate>
        }
      />
      <SectionBoundary title="Products">
        <ProductList onConnect={connect} />
      </SectionBoundary>
      <SectionBoundary title="Recent webhook deliveries">
        <RecentDeliveries />
      </SectionBoundary>
      <ConnectProductDialog open={connecting} onOpenChange={setConnecting} />
    </>
  );
}
