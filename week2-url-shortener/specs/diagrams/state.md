# State Diagram — URL Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Active : POST /api/v1/urls (valid URL shortened)

    Active --> Active : GET /{shortCode} (redirect, click recorded)
    Active --> Expired : expires_at reached / redirect after expiry
    Active --> Deleted : admin or owner deletes short URL

    Expired --> Deleted : retention window elapses / manual purge
    Expired --> Active : expires_at extended or cleared

    Deleted --> [*]

    note right of Active
        Redirects return 302.
        Analytics are tracked.
    end note

    note right of Expired
        Redirects return 410 Gone.
        No new clicks recorded.
    end note

    note right of Deleted
        Lookups return 404 Not Found.
    end note
```

## Transitions

| From    | To       | Event                                                        |
|---------|----------|-------------------------------------------------------------|
| (start) | Active   | A valid URL is shortened (REQ-SHORT-001)                     |
| Active  | Active   | Short code is visited; click count / referrer recorded (REQ-SHORT-005) |
| Active  | Expired  | `expires_at` passes, or a redirect is requested after expiry (REQ-SHORT-007) |
| Active  | Deleted  | Short URL is removed by owner/admin                          |
| Expired | Deleted  | Retention window elapses or record is purged                |
| Expired | Active   | Expiration is extended or cleared                            |
| Deleted | (end)    | Record removed; lookups return 404 (REQ-SHORT-004)          |