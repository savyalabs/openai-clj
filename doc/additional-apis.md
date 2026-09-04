# Additional APIs

- `openai.uploads`: multipart uploads and parts.
- `openai.containers`: container and file CRUD/content.
- `openai.conversations`: conversation and item CRUD.
- `openai.skills`: skills, versions, and content downloads.
- `openai.videos`: generation, remix, edit, extend, download, and characters.
- `openai.moderations`: content moderation.
- `openai.safety`: retrieve safety alerts.
- `openai.completions`: legacy text completions.

Retrieve a safety alert by ID:

```clojure
(require '[openai.safety :as safety])

(safety/retrieve client "safety_alert_...")
;; => {:id "safety_alert_..."
;;     :created-at 1790000000.0
;;     :error-type :...
;;     :model "..."
;;     :request-id "req_..."
;;     :request-paused false
;;     :response-id "resp_..."
;;     :reason "..."} ; optional when reported by the API
```

Each list helper returns a realized vector from all pages. Binary inputs accept
paths, byte arrays, and input streams where the SDK supports them.
