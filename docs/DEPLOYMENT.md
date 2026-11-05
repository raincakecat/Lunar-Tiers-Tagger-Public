# Deployment

The rank API runs separately from the Discord bot and reads a local SQLite database without modifying it. Copy api/config.example.json to an untracked local configuration and supply your own database path and server/mode mappings. Keep credentials and personal data outside this repository.

Run the service with Node 24.17 or newer. Bind it to loopback behind an HTTPS reverse proxy that exposes only POST /v1/ranks/lookup. Configure trusted proxy headers only for a proxy you control. See API.md for the public response contract. The built-in rate limits are per process.

For connected client builds, pass -PrankApiUrl=https://YOUR_HOST/v1/ranks/lookup to Gradle. Test using synthetic accounts and verify that private paths and configuration are inaccessible before publishing.
