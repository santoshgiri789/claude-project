# ER Diagram — Data Model

```mermaid
erDiagram
    URL ||--o{ CLICK_EVENT : "records"

    URL {
        bigint id PK
        varchar short_code UK "unique, indexed URL-safe code"
        varchar original_url "validated http/https URL"
        int click_count "denormalized total clicks"
        timestamp last_accessed_at "nullable"
        timestamp created_at
        timestamp expires_at "nullable, optional expiry"
        varchar status "ACTIVE | EXPIRED | DELETED"
    }

    CLICK_EVENT {
        bigint id PK
        bigint url_id FK "references URL.id"
        varchar referrer "nullable HTTP Referer"
        varchar client_ip "nullable"
        timestamp accessed_at
    }
```

## Notes

- `URL.short_code` is unique and indexed to guarantee O(1) lookups (REQ-SHORT-002).
- `URL.click_count` and `URL.last_accessed_at` are denormalized aggregates updated on
  each redirect for fast analytics reads (REQ-SHORT-005).
- Each redirect also appends a `CLICK_EVENT` row, preserving per-hit referrer history
  for the analytics endpoint (REQ-SHORT-006).
- `URL.expires_at` is nullable — a null value means the short URL never expires
  (REQ-SHORT-007).