"use client";

import { useParams } from "next/navigation";
import { PaybillDetail } from "@/components/paybills/paybill-detail";
import { PAYBILL_SECTIONS } from "./sections";

export function PaybillDetailClient() {
  const { uuid } = useParams<{ uuid: string }>();
  return <PaybillDetail uuid={uuid} sections={PAYBILL_SECTIONS} />;
}
