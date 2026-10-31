import { AppSidebar } from "@/components/app-sidebar";
import { OfflineBanner, ServiceStatusPill } from "@/components/connection-status";
import { ConsoleAccessGate } from "@/components/console-access-gate";
import { ThemeToggle } from "@/components/theme-toggle";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { UserMenu } from "@/components/user-menu";

export default function ConsoleLayout({ children }: LayoutProps<"/">) {
  return (
    <SidebarProvider>
      <AppSidebar />
      {/* min-w-0 keeps wide tables scrolling inside their card instead of widening the page. */}
      <SidebarInset className="min-w-0">
        <OfflineBanner />
        <header className="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background/90 px-4 backdrop-blur">
          <SidebarTrigger className="-ml-1" />
          <div className="ml-auto flex items-center gap-1">
            <ServiceStatusPill />
            <ThemeToggle />
            <UserMenu />
          </div>
        </header>
        <main className="flex min-w-0 flex-1 flex-col gap-6 p-4 md:p-8">
          <ConsoleAccessGate>{children}</ConsoleAccessGate>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
