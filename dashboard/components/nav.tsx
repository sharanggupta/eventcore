"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "Overview" },
  { href: "/events", label: "Events" },
  { href: "/deliveries", label: "Deliveries" },
  { href: "/webhooks", label: "Webhooks" },
  { href: "/consumers", label: "Consumers" },
];

export function Nav() {
  const pathname = usePathname();
  const isActive = (href: string) =>
    href === "/" ? pathname === "/" : pathname.startsWith(href);

  return (
    <nav className="flex flex-col gap-1">
      {LINKS.map((link) => (
        <Link
          key={link.href}
          href={link.href}
          className={`rounded-xl px-4 py-2.5 text-sm font-medium transition ${
            isActive(link.href)
              ? "bg-white/10 text-white shadow-inner"
              : "text-slate-400 hover:bg-white/5 hover:text-slate-200"
          }`}
        >
          {link.label}
        </Link>
      ))}
    </nav>
  );
}
