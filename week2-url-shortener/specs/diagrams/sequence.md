# Sequence Diagram — Shorten then Redirect

## Shorten a URL

```mermaid
sequenceDiagram
    actor Client
    participant Controller as UrlController
    participant Service as UrlService
    participant Repository as UrlRepository
    participant DB as Database (H2)

    Client->>Controller: POST /api/v1/urls { url, expires_at? }
    Controller->>Service: shorten(request)
    Service->>Service: validate URL (scheme, syntax, SSRF check)
    alt invalid or malicious URL
        Service-->>Controller: ValidationException
        Controller-->>Client: 400 Bad Request { error }
    else valid URL
        Service->>Repository: findByOriginalUrl(url)
        Repository->>DB: SELECT * FROM url WHERE original_url = ?
        DB-->>Repository: existing row or empty
        Repository-->>Service: Optional<Url>
        alt duplicate exists
            Service-->>Controller: existing short_code
            Controller-->>Client: 200 OK { short_code, short_url }
        else new URL
            Service->>Service: generate unique short_code
            Service->>Repository: save(Url)
            Repository->>DB: INSERT INTO url (...)
            DB-->>Repository: persisted row
            Repository-->>Service: Url
            Service-->>Controller: UrlResponse
            Controller-->>Client: 201 Created { short_code, short_url }
        end
    end
```

## Redirect via a short code

```mermaid
sequenceDiagram
    actor Client
    participant Controller as RedirectController
    participant Service as UrlService
    participant Repository as UrlRepository
    participant DB as Database (H2)

    Client->>Controller: GET /{shortCode}
    Controller->>Service: resolve(shortCode, referer)
    Service->>Repository: findByShortCode(shortCode)
    Repository->>DB: SELECT * FROM url WHERE short_code = ?
    DB-->>Repository: row or empty
    Repository-->>Service: Optional<Url>
    alt short code not found
        Service-->>Controller: NotFoundException
        Controller-->>Client: 404 Not Found { error }
    else expired
        Service-->>Controller: GoneException
        Controller-->>Client: 410 Gone { error }
    else active
        Service->>Service: record click (count++, last_accessed, referrer)
        Service->>Repository: save(Url + ClickEvent)
        Repository->>DB: UPDATE url; INSERT INTO click_event
        DB-->>Repository: ok
        Service-->>Controller: original_url
        Controller-->>Client: 302 Found (Location: original_url)
    end
```