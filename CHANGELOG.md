# Changelog

## [0.20.0] - 2026-08-30

### Changed

- Breaking: explicitly empty `:allowed-tools` and `:allowed-domains` lists now
  throw `:openai/error :empty-allow-list` in both stable and beta Responses
  namespaces. Callers that pass an empty list must omit the key to keep the
  existing unrestricted behavior, or provide at least one allowed value.

### Fixed

- `parse-structured-output` now enforces `minItems` and `maxItems` for array
  schemas.
- String `minLength` and `maxLength` constraints now count Unicode code points,
  so astral characters such as emoji count as one character.

## [0.19.1] - 2026-08-30

### Fixed

- Correct the README's tracked SDK version, which said openai-java 4.51.0 while
  the library depends on 4.52.0, and point the release link at the matching tag.

## [0.19.0] - 2026-08-26

### Added

- Lossless response capture: `create-response` and `get-response` accept
  `:lossless? true` to add the complete parsed SDK response under
  `:openai/raw` alongside the existing curated top-level map. Additive —
  the default curated shape is unchanged.
- Advanced client configuration: `client` accepts `:admin-api-key`,
  `:headers`, `:proxy`, `:executor`, `:stream-handler-executor`,
  `:log-level`, and `:workload-identity`, layered on the existing
  `:api-key`/`:organization`/`:project`/`:base-url`/`:timeout-ms`/
  `:max-retries`/`:azure-service-version` options.
- Optional blocking Amazon Bedrock transport in `openai.bedrock`
  (require it explicitly to add the `:bedrock` alias): `client` builds a
  Bedrock-backed `OpenAIClient` from `:endpoint` (`:mantle` or `:runtime`),
  `:aws-region`, and the standard AWS credential options, or a zero-argument
  Clojure function/`Supplier` token provider.
- Realtime reliability: `connect` gains `:auto-reconnect?` with exponential
  backoff and jitter (`:reconnect-base-delay-ms`, `:reconnect-max-delay-ms`,
  `:reconnect-jitter`, `:reconnect-max-attempts`,
  `:reconnect-max-duration-ms`) and idle detection (`:heartbeat-interval-ms`,
  `:idle-timeout-ms`), which emits a `:connection.idle-timeout` event when no
  activity is observed within the configured window.
- Lazy pagination siblings — `list-models-lazy`, `list-chat-completions-lazy`,
  `list-input-items-lazy` (and other list operations) — return a lazy
  sequence honoring optional `:max-items` and `:max-pages`, alongside the
  existing eager `list-*` functions that follow every page automatically.
- Local wire-level protocol fixtures for testing request/response shapes
  without live API calls.
- `examples/` cookbook: batch processing, realtime, and tool-calling
  production workflow walkthroughs.

## [0.18.2] - 2026-08-17

### Changed

- Bump `com.openai/openai-java` to 4.52.0. New Responses streaming events
  (shell-call command/output events) and image-generation partial-image fields
  are surfaced through the generic stream-event normalization. No wrapper API
  change; the release adds no new operations (Bedrock runtime support ships in a
  separate `openai-java-bedrock` module that this wrapper does not include).

## [0.18.1] - 2026-08-17

### Fixed

- Realtime `dispatch!` blocks instead of silently discarding audio and
  response-delta events when the bounded queue is full.
- `parse-structured-output` validates the common JSON Schema constraints it
  documents (minimum/maximum, minLength/maxLength, nested properties and items).

## [0.18.0] - 2026-08-14

### Added
- `openai.beta.responses` wraps the beta Responses API: create, streaming
  create, retrieve, streaming retrieve, cancel, delete, compact, input-item
  listing, and input-token count. The create request takes beta flags,
  multi-agent settings, tools, text configuration, conversation, moderation,
  and multimodal message content.
- Stable Responses requests take `:prompt`, `:context-management`, and
  `:prompt-cache-retention`. Stable and beta response maps carry `:prompt` and
  `:prompt-cache-retention` when the response holds them.
- Both input unions accept every item variant the SDK defines, so an output
  item returned by a response can be sent back as input on the next turn. This
  is what a multi-turn tool conversation needs when the caller holds the
  history instead of passing `:previous-response-id`.

## [0.17.0] - 2026-08-14

### Changed
- Upgraded `com.openai/openai-java` to 4.51.0.
- The `:error` value of an MCP call output item is a map that describes the failure, in
  place of a string. The map carries `:type` and the fields of the reported variant: a
  protocol error, a tool execution error, or an HTTP error.
- OpenAI recognizes the `ultrafast` service tier, the
  `tenant_workload_identity_access_token_issued` audit-log event type, and the
  `gpt-5.6-cyber`, `gpt-daybreak-blue-latest`, `gpt-daybreak-red-latest`, `gpt-5.5-pro`,
  and `gpt-5.5-2026-04-23` model identifiers. Service tiers, audit-log event types, and
  model identifiers pass through as given, so a call site sets them with no change.

### Deprecated
- OpenAI deprecated the Sora video endpoints that `openai.videos` wraps.

## [0.16.0] - 2026-08-13

### Fixed
- `list-input-items` now accepts an options map, so `:after`, `:include`,
  `:limit`, and `:order` reach the API. The one-argument form is unchanged.

### Changed
- Video maps return `:model`, `:size`, and `:seconds` as keywords. Each is an
  enum in the SDK, and image maps already return the equivalent fields as
  keywords. Code that compares these three fields against a string must compare
  against a keyword instead.

### Added
- The test matrix covers Clojure 1.10, 1.11, and 1.12.

## [0.15.2] - 2026-08-13

### Changed
- Docstrings and prose documentation rewritten in Simplified Technical English.
  No behavior change.

## [0.15.1] - 2026-08-03
### Fixed
- Reading a stored function-call-output item no longer leaks the Java union
  wrapper into `:output`. A string output previously came back as
  `"Output{string=sunny}"` instead of `"sunny"`, which broke feeding the item
  straight back into a follow-up request. Content-list outputs are now returned
  as structured data instead of a stringified wrapper.

## [0.15.0] - 2026-08-03
### Added
- Added optional `:name` and `:namespace` support for function-call-output
  request and response maps.

### Changed
- Upgraded `com.openai/openai-java` to 4.50.0.
- `gpt-5.5` is usable through the existing model string passthrough and needed
  no library change.

## [0.14.0] - 2026-07-31
### Added
- Added the Content Provenance Checks API via `openai.content-provenance-checks/create`.
- Added transcription multi-language support via `:languages`.

### Changed
- Upgraded `com.openai/openai-java` to 4.48.0.
- The `:fast` service tier is now accepted.

## [0.13.0] - 2026-07-23
### Added
- Added organization and project spend-limit retrieve, update, and delete wrappers.
- Added service-account API key creation for projects.

### Changed
- Upgraded `com.openai/openai-java` to 4.45.0.

## [0.12.2] - 2026-07-22
### Documentation
- Restated coverage as idiomatic parity with the Java SDK: every non-deprecated operation is wrapped. Clarified that async clients, raw-response accessors, and per-call `RequestOptions` are non-endpoint variants, not coverage gaps.

## [0.12.1] - 2026-07-21
### Documentation
- Documented OpenAI-compatible provider support: `:base-url` targets Azure,
  Groq, DeepSeek, Mistral, xAI, Together, Fireworks, and local servers.
- Reframed the intro to reflect OpenAI plus OpenAI-compatible providers.
- Corrected the stale install coordinates in the README.

## [0.12.0] - 2026-07-21
### Added
- `openai.chatkit`: wraps the beta ChatKit API (session create/cancel, thread
  retrieve/list/delete, thread-item listing).
- The deprecated Assistants API is intentionally excluded.

## [0.11.0] - 2026-07-21
### Added
- Added the `:owner-project-access` filter to `api-key-list`.

## [0.10.0] - 2026-07-21
### Changed
- Upgraded `com.openai/openai-java` to 4.43.0 and added `:owner-project-access`
  to project API key maps.

## [0.9.0] - 2026-07-16
### Added
- Added the `openai.realtime` namespace with Realtime WebSocket sessions,
  client-secret, session, transcription, and translation helpers, and SIP call
  control.
- Expanded Responses tool coverage with image generation, computer,
  shell/local shell, apply patch, custom, tool search, and MCP approval tools,
  plus their call-output input items.
- Added lossless conversion for all Responses output-item variants.
- Added normalization for the full Responses streaming-event surface.
- Added a structured-output helper that parses `json_schema` response text and
  validates it against the requested schema.

## [0.8.0] - 2026-07-11
### Changed
- **BREAKING (admin):** Admin API functions now take positional resource IDs
  (project, group, user, role, …) followed by an optional kebab-case opts map,
  replacing the single params map used in 0.7.0.
- Reimplemented the Admin API and curated every service response converter as
  hand-written, type-hinted interop returning present-only kebab-case maps;
  removed the runtime-reflection admin engine and generic JSON-dump conversion
  (retained only for webhook event unwrapping).
- Strengthened no-network unit-test coverage across the service namespaces.

## [0.7.0] - 2026-07-11
### Added
- Added stable images, audio, moderations, legacy completions, vector stores,
  uploads, containers, conversations, fine-tuning, evals, skills, videos,
  webhooks, and organization/project admin APIs.
- Added stored Chat Completions CRUD and model deletion.

### Changed
- Upgraded `com.openai/openai-java` from 4.41.0 to 4.42.0.
- Added GPT-5.6-sol reasoning mode, prompt-cache options, programmatic tool
  calling, and cache-write token usage.

## [0.6.0] - 2026-07-10
### Added
- Added Chat Completions API compatibility support, including create and streaming helpers.

## [0.5.2] - 2026-07-09
### Changed
- Reorganized the README into a cljdoc article tree under `doc/` (Tools, Streaming, Embeddings/Files/Batches, Azure, Responses & Errors, Migrating). Documentation content is unchanged; no API changes.

All notable changes to this project are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project adheres to [Semantic Versioning](https://semver.org/).

## [0.5.1] - 2026-07-09
### Fixed
- POM now includes the project description, homepage URL, and full SCM connection metadata, so Clojars shows a description/homepage and cljdoc has complete source-link data.
