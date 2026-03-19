import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "HR Assistant",
  description: "AI-powered HR Assistant for profile management, job discovery, and more",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
