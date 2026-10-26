import { NotFoundCard } from "@/components/not-found-card";

// Rendered when a console page calls notFound(), e.g. for a paybill id that doesn't exist.
export default function ConsoleNotFound() {
  return <NotFoundCard description="It may have been removed, or the link is wrong." />;
}
