/**
 * API keys and request authentication: admin-token-guarded issuance and revocation
 * (only SHA-256 hashes stored), and the filter that demands {@code X-API-Key} on
 * {@code /v1/**} while keeping health, metrics, and docs public.
 */
package dev.eventcore.security;
