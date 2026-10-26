import { AppSidebar } from "@/components/app-sidebar";
import { OfflineBanner, ServiceStatusPill } from "@/components/connection-status";
import { ThemeToggle } from "@/components/theme-toggle";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { UserMenu } from "@/components/user-menu";

export default function ConsoleLayout({ children }: LayoutProps<"/">) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <OfflineBanner />
        <header className="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background/90 px-4 backdrop-blur">
          <SidebarTrigger className="-ml-1" />
          <div className="ml-auto flex items-center gap-1">
            <ServiceStatusPill />
            <ThemeToggle />
            <UserMenu />
          </div>
        </header>
        <main className="flex flex-1 flex-col gap-6 p-4 md:p-8">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
