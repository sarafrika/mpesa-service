import type { ReactNode } from "react";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { cn } from "@/lib/utils";

type SectionCardProps = {
  title?: ReactNode;
  description?: ReactNode;
  /** Top-right controls such as filters or a "View all" link. */
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
};

/** A titled card: the building block of every console screen. */
export function SectionCard({
  title,
  description,
  actions,
  children,
  className,
  contentClassName,
}: SectionCardProps) {
  return (
    <Card className={className}>
      {(title || description || actions) && (
        <CardHeader>
          {title && <CardTitle className="font-semibold">{title}</CardTitle>}
          {description && <CardDescription>{description}</CardDescription>}
          {actions && <CardAction className="flex flex-wrap gap-2">{actions}</CardAction>}
        </CardHeader>
      )}
      <CardContent className={cn("flex flex-col gap-4", contentClassName)}>{children}</CardContent>
    </Card>
  );
}
