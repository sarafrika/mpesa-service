"use client";

import {
  ArrowLeftRight,
  Boxes,
  CreditCard,
  Inbox,
  LayoutDashboard,
  type LucideIcon,
  ScrollText,
  ShieldCheck,
  SquareCheckBig,
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "next-auth/react";
import { type ComponentType, Suspense } from "react";
import { ApprovalsBadge } from "@/components/approvals/approvals-badge";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { visibleNav } from "@/lib/nav";
import type { ConsoleRole } from "@/lib/roles";

type NavItem = {
  title: string;
  href: string;
  icon: LucideIcon;
  ready?: boolean;
  /** A count beside the item, e.g. requests waiting for a checker. */
  badge?: ComponentType;
  /** Hidden unless the user has one of these; the page itself still shows a 403 to a direct visit. */
  needs?: ConsoleRole | readonly ConsoleRole[];
};

const NAV: { label: string; items: NavItem[] }[] = [
  {
    label: "Monitor",
    items: [
      { title: "Overview", href: "/", icon: LayoutDashboard, ready: true },
      { title: "Transactions", href: "/transactions", icon: ArrowLeftRight, ready: true },
      { title: "Reconciliation", href: "/reconciliation", icon: SquareCheckBig, ready: true },
    ],
  },
  {
    label: "Manage",
    items: [
      { title: "Paybills & tills", href: "/paybills", icon: CreditCard, ready: true },
      { title: "Products", href: "/products", icon: Boxes, ready: true },
      {
        title: "Approvals",
        href: "/approvals",
        icon: ShieldCheck,
        ready: true,
        badge: ApprovalsBadge,
      },
    ],
  },
  {
    label: "System",
    items: [
      { title: "Callback inbox", href: "/callbacks", icon: Inbox, ready: true },
      { title: "Audit log", href: "/audit", icon: ScrollText, ready: true, needs: "mpesa-admin" },
    ],
  },
];

export function AppSidebar() {
  const { data: session } = useSession();
  const groups = visibleNav(NAV, session?.roles);
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link href="/">
                <span className="flex size-8 items-center justify-center rounded-lg bg-muted">
                  <Image src="/sarafrika-symbol.svg" alt="" width={22} height={22} />
                </span>
                <span className="flex flex-col leading-tight">
                  <span className="font-semibold">Sarafrika</span>
                  <span className="text-muted-foreground text-xs">M-Pesa console</span>
                </span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {groups.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              {/* Below a dynamic route the pathname is only known at request time. */}
              <Suspense fallback={<NavMenu items={group.items} />}>
                <ActiveNavMenu items={group.items} />
              </Suspense>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  );
}

function ActiveNavMenu({ items }: { items: NavItem[] }) {
  return <NavMenu items={items} pathname={usePathname()} />;
}

function NavMenu({ items, pathname }: { items: NavItem[]; pathname?: string }) {
  return (
    <SidebarMenu>
      {items.map((item) => (
        <SidebarMenuItem key={item.href}>
          {item.ready ? (
            <>
              <SidebarMenuButton
                asChild
                isActive={
                  pathname !== undefined &&
                  (pathname === item.href ||
                    (item.href !== "/" && pathname.startsWith(`${item.href}/`)))
                }
                tooltip={item.title}
              >
                <Link href={item.href}>
                  <item.icon />
                  <span>{item.title}</span>
                </Link>
              </SidebarMenuButton>
              {item.badge && <item.badge />}
            </>
          ) : (
            <>
              <SidebarMenuButton disabled tooltip={`${item.title} (coming soon)`}>
                <item.icon />
                <span>{item.title}</span>
              </SidebarMenuButton>
              <SidebarMenuBadge className="text-muted-foreground">Soon</SidebarMenuBadge>
            </>
          )}
        </SidebarMenuItem>
      ))}
    </SidebarMenu>
  );
}
