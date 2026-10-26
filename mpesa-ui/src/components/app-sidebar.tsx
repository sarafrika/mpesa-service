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
import { Suspense } from "react";
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

type NavItem = { title: string; href: string; icon: LucideIcon; ready?: boolean };

// Items without `ready` arrive with the admin API; they stay visible so the shape is clear.
const NAV: { label: string; items: NavItem[] }[] = [
  {
    label: "Monitor",
    items: [
      { title: "Overview", href: "/", icon: LayoutDashboard, ready: true },
      { title: "Transactions", href: "/transactions", icon: ArrowLeftRight, ready: true },
      { title: "Reconciliation", href: "/reconciliation", icon: SquareCheckBig },
    ],
  },
  {
    label: "Manage",
    items: [
      { title: "Paybills & tills", href: "/paybills", icon: CreditCard, ready: true },
      { title: "Products", href: "/products", icon: Boxes },
      { title: "Approvals", href: "/approvals", icon: ShieldCheck },
    ],
  },
  {
    label: "System",
    items: [
      { title: "Callback inbox", href: "/callbacks", icon: Inbox },
      { title: "Audit log", href: "/audit", icon: ScrollText },
    ],
  },
];

export function AppSidebar() {
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
        {NAV.map((group) => (
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
