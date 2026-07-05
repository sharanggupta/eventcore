import type { Metadata } from "next";
import { Nav } from "@/components/nav";
import "./globals.css";

export const metadata: Metadata = {
  title: "EventCore",
  description: "Your system's memory, and its megaphone",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <div className="mx-auto flex min-h-screen max-w-7xl flex-col gap-6 p-4 lg:flex-row lg:p-6">
          <aside className="glass flex shrink-0 flex-col p-4 lg:sticky lg:top-6 lg:h-[calc(100vh-3rem)] lg:w-52">
            <div className="mb-4 px-2 lg:mb-8">
              <div className="text-lg font-bold text-white">
                Event<span className="accent">Core</span>
              </div>
              <div className="text-[11px] text-slate-500">tenant dashboard</div>
            </div>
            <Nav />
            <div className="mt-4 px-2 text-[11px] text-slate-600 lg:mt-auto">
              dedicated instance · EU
            </div>
          </aside>
          <main className="min-w-0 flex-1">{children}</main>
        </div>
      </body>
    </html>
  );
}
