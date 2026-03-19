/** @type {import('next').NextConfig} */
const nextConfig = {
  // Proxy API requests to Spring Boot backend during development
  async rewrites() {
    return [
      {
        source: "/api/agent/:path*",
        destination: "http://localhost:8080/api/agent/:path*",
      },
    ];
  },
};

module.exports = nextConfig;
