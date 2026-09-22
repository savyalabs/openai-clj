# openai-clj

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/openai-clj.svg)](https://clojars.org/net.clojars.savya/openai-clj)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/openai-clj)](https://cljdoc.org/d/net.clojars.savya/openai-clj/CURRENT)
[![test](https://github.com/savyalabs/openai-clj/actions/workflows/test.yml/badge.svg)](https://github.com/savyalabs/openai-clj/actions/workflows/test.yml)

Clojure client for the OpenAI API and OpenAI-compatible providers. It uses the
official Java SDK.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://platform.openai.com/docs/api-reference/responses"><img src="https://img.shields.io/badge/OpenAI-412991?style=flat&logo=openai&logoColor=fff" alt="OpenAI" /></a>

## Installation

deps.edn:

```clojure
net.clojars.savya/openai-clj {:mvn/version "0.33.0"}
```

Leiningen:

```clojure
[net.clojars.savya/openai-clj "0.33.0"]
```

Supported Clojure versions: 1.10, 1.11, and 1.12.

Tracks [`com.openai/openai-java` 4.67.0](https://github.com/openai/openai-java/releases/tag/v4.67.0).

## Providers

Amazon Bedrock support is optional. Add the `:bedrock` alias to use the
blocking Bedrock transport:

```clojure
(require '[openai.bedrock :as bedrock])

(def client
  (bedrock/client {:endpoint :runtime
                   :aws-region "us-west-2"
                   :aws-profile "default"}))
```

The alias adds `com.openai/openai-java-bedrock` and its AWS SDK dependencies;
default consumers do not load them. Credentials can also be supplied with
`:aws-access-key-id`, `:aws-secret-access-key`, and `:aws-session-token`, or
discovered through the AWS credential chain.

Every function takes a client. The client `:base-url` points to an endpoint
that uses the OpenAI wire protocol. The same code works with these providers:

| Provider | `:base-url` |
|---|---|
| OpenAI (default) | omit |
| Azure OpenAI | your resource endpoint (see [Azure OpenAI](doc/azure.md)) |
| Groq | `https://api.groq.com/openai/v1` |
| DeepSeek | `https://api.deepseek.com` |
| Mistral | `https://api.mistral.ai/v1` |
| xAI (Grok) | `https://api.x.ai/v1` |
| Together | `https://api.together.xyz/v1` |
| Fireworks | `https://api.fireworks.ai/inference/v1` |
| Local (Ollama, vLLM, LM Studio) | e.g. `http://localhost:11434/v1` |

```clojure
(def client (openai/client {:api-key "gsk_..."
                            :base-url "https://api.groq.com/openai/v1"}))

(openai/create-chat-completion
 client
 {:model "llama-3.3-70b-versatile"
  :messages [{:role :user :content "Hello"}]})
```

Use Chat Completions (`create-chat-completion`) with the most providers. The
Responses API is OpenAI-specific. Most compatible providers do not implement it.
Only routes that a provider serves work. The library does not cover
provider-specific extensions outside the OpenAI protocol.

## Documentation

- [Examples cookbook](examples/README.md)
- [Tools](doc/tools.md)
- [Streaming](doc/streaming.md)
- [Embeddings, Files and Batches](doc/embeddings-files-batches.md)
- [Images and Audio](doc/images-and-audio.md)
- [Vector Stores](doc/vector-stores.md)
- [Fine-tuning](doc/fine-tuning.md)
- [Evals](doc/evals.md)
- [Admin](doc/admin.md)
- [Webhooks](doc/webhooks.md)
- [Skills, Videos, Containers, Uploads and Conversations](doc/additional-apis.md)
- [Azure OpenAI](doc/azure.md)
- [Responses and Errors](doc/responses-and-errors.md)
- [Migrating from wkok/openai-clojure](doc/migrating.md)

## Usage

```clojure
(require '[openai.core :as openai])

(def client (openai/client)) ; reads OPENAI_API_KEY

(def configured-client
  (openai/client {:api-key "sk-..."
                  :organization "org_..."
                  :project "proj_..."
                  :base-url "https://api.openai.com/v1"
                  :timeout-ms 60000
                  :max-retries 2}))

The constructor also accepts `:admin-api-key`, `:headers`, `:proxy` (a
`java.net.Proxy` or `{:host "..." :port 8080}`), `:executor`,
`:stream-handler-executor`, `:log-level` (`:off`, `:info`, `:error`, or
`:debug`), and a typed SDK `:workload-identity`.

(openai/create-response
 client
 {:model "gpt-5.2"
  :input "Write one sentence about Clojure maps."
  :instructions "Be precise."
  :max-output-tokens 256
  :temperature 0.2
  :top-p 1.0
  :metadata {:app "docs"}
  :store true
  :reasoning {:effort :low}})
;; => {:id "resp_..."
;;     :model "gpt-5.2"
;;     :status :completed
;;     :output [{:type :message
;;               :role :assistant
;;               :id "msg_..."
;;               :content [{:type :text :text "Clojure maps are ..."}]}]
;;     :text "Clojure maps are ..."
;;     :usage {:input-tokens 14 :output-tokens 12 :total-tokens 26}
;;     :created-at 1790000000.0}
```

## Responses API

Request maps support `:model`, `:input`, `:instructions`,
`:max-output-tokens`, `:max-tool-calls`, `:temperature`, `:top-p`,
`:top-logprobs`, `:metadata`, `:previous-response-id`, `:store`, `:user`,
`:reasoning`, `:tools`, `:tool-choice`, `:parallel-tool-calls`, `:background`,
`:include`, `:truncation`, `:prompt-cache-key`, `:safety-identifier`,
`:service-tier`, `:json-schema`, `:verbosity` (`:low`/`:medium`/`:high`),
`:conversation` (a conversation id string), `:stream-options`
(`{:include-obfuscation true}`), and `:moderation` (`{:model "..."}`).

Input can be a string or a vector of message items. Message content can be a
string or a vector of multimodal parts:

```clojure
{:model "gpt-5.2"
 :input [{:role :user
          :content [{:type :text :text "Summarize this image."}
                    {:type :image
                     :image-url "https://example.test/chart.png"
                     :detail :high}
                    {:type :file
                     :filename "notes.pdf"
                     :file-data "data:application/pdf;base64,..."}]}]}
```

Structured outputs use `:json-schema`:

```clojure
(def request
  {:model "gpt-5.2"
   :input "Return an answer object."
   :json-schema {:name "answer"
                 :description "One answer"
                 :strict true
                 :schema {:type "object"
                          :properties {:answer {:type "string"}}
                          :required ["answer"]}}})
```

Parse and validate the returned JSON against the same schema:

```clojure
(def response (openai/create-response client request))
(openai/parse-structured-output response (:json-schema request))
;; => {:data {"answer" "..."} :errors []}
```

Responses tools cover `:function`, `:web-search`, `:file-search`,
`:code-interpreter`, `:programmatic-tool-calling`, `:image-generation`,
`:computer`, `:local-shell`, `:shell`, `:apply-patch`, `:custom`,
`:tool-search`, and `:mcp`. Vector input accepts the matching
`:function-call-output`, `:computer-call-output`, `:local-shell-call-output`,
`:shell-call-output`, `:apply-patch-call-output`, `:custom-tool-call-output`,
`:tool-search-output`, and `:mcp-approval-response` items.

Response maps preserve all SDK output-item variants as kebab-case Clojure maps.
This includes `:configuration-update` items, whose optional reasoning effort is
returned as `{:reasoning {:effort <keyword>}}`. Response API errors expose an
optional `:misalignment` map with `:detailed-explanation`, `:error-type` (a
kebab-case keyword), and `:steer` when reported.
Pass `:lossless? true` to `create-response`, or as the optional argument to
`get-response`, `cancel-response`, and `compact`, to retain the curated map and
also include the SDK's complete parsed JSON under `:openai/raw`.
`openai/stream` normalizes each Responses stream event. It calls its callback
with the resulting `:type`-keyed map. `openai/stream-text` wraps text deltas.

## Realtime API

`openai.realtime` provides normalized WebSocket events through callbacks or a
blocking queue:

```clojure
(require '[openai.realtime :as realtime])

(def connection
  (realtime/connect {:api-key (System/getenv "OPENAI_API_KEY")
                     :model "gpt-realtime"}))

(realtime/send! connection
                {:type :session.update
                 :session {:type :realtime
                           :instructions "Be concise."}})
(realtime/poll! connection 5000) ; normalized server event, or nil
(realtime/close! connection)
```

Reliability features are opt-in. `:auto-reconnect? true` enables exponential
backoff retries; configure `:reconnect-max-attempts` or
`:reconnect-max-duration-ms`, plus the base/max delay and jitter options.
`:heartbeat-interval-ms` enables OkHttp protocol pings for an owned client, and
`:idle-timeout-ms` emits `:connection.idle-timeout` when no server messages are
received in that window. Existing behavior and defaults are unchanged.

The namespace also exposes client-secret creation, legacy session and
transcription-session creation, translation client secrets and WebSockets, and
SIP `accept-call`, `hangup-call`, `refer-call`, and `reject-call` operations.

## Live API

`openai.live` wraps the WebRTC-based Live session service. `live-create` starts
a session from a model configuration and browser SDP offer. Session lifecycle
operations are `session-accept`, `session-hangup`, `session-refer`,
`session-reject`, `session-fork`, and `session-download-recording`.

```clojure
(require '[openai.live :as live])

(live/live-create client
                  {:session {:model "gpt-live-1"
                             :instructions "Be concise."}
                   :transport {:sdp browser-sdp-offer}})
;; => {:session {:id "live_..."}
;;     :transport {:sdp "..."}}
```

The SDK's Live `forks` and `sideband` services expose no operations in 4.62.0,
so this namespace intentionally adds no wrappers for them.

## Beta Agents API

Multi-agent orchestration: durable `Agent` definitions, `Session`s that run
turns against an agent, subagents spawned within a session, sandboxed
`Environment`s, and `Vault`s holding tool credentials.

```clojure
(require '[openai.beta.agents :as agents]
         '[openai.beta.agents.sessions :as sessions]
         '[openai.beta.agents.sessions.turns :as turns]
         '[openai.beta.agents.sessions.items :as items]
         '[openai.beta.agents.sessions.events :as events]
         '[openai.beta.agents.sessions.artifacts :as artifacts]
         '[openai.beta.agents.sessions.subagents :as subagents]
         '[openai.beta.agents.sessions.subagents.items :as subagent-items]
         '[openai.beta.agents.sessions.subagents.turns :as subagent-turns]
         '[openai.beta.agents.sessions.subagents.turns.items :as turn-items]
         '[openai.beta.agents.environments :as environments]
         '[openai.beta.agents.environments.files :as env-files]
         '[openai.beta.agents.environments.templates :as env-templates]
         '[openai.beta.agents.vaults :as vaults]
         '[openai.beta.agents.vaults.credentials :as credentials])

;; Agents: create, retrieve, update, delete, list
(agents/create client {:model "gpt-5.1" :name "researcher"})
(agents/retrieve client "agent_...")
(agents/list client)

;; Sessions: create (optionally streaming), retrieve, update, delete, list
(sessions/session-create client {:agent-id "agent_..."})
(sessions/session-create-streaming client {:agent-id "agent_..."})

;; Turns and items within a session
(turns/list-turns client "sess_..." {:order :desc})
(items/list-items client "sess_..." {:limit 20})

;; Session-level events and artifacts
(events/create client "sess_..." {...})
;; Stream callbacks receive maps such as {:type :agent.session.environment.reset
;;                                       :event-id "event_..." :environment-id "env_..."
;;                                       :reset-count 1 :turn-id nil}
(artifacts/list client "sess_...")
(artifacts/content client "sess_..." "artifact_...")

;; Subagents spawned within a session, and their own turns/items
(subagents/subagent-retrieve client "sess_..." "subagent_...")
(subagent-items/item-list client "sess_..." "subagent_..." {:limit 20})
(subagent-turns/turn-list client "sess_..." "subagent_..." {:order :desc})
(turn-items/item-list client "sess_..." "subagent_..." "turn_..." {})

;; Sandboxed environments, plus their files and templates
(environments/retrieve client "env_...")
(env-files/create client "env_..." {...})
(env-templates/list client)

;; Vaults and the credentials stored in them
(vaults/create-vault client {:name "prod-creds"})
(credentials/create-credential client "vault_..." {:name "github"
                                                     :auth {:type :static-bearer
                                                            :token "..."}})
(credentials/create-credential client "vault_..." {:name "github-env"
                                                     :auth {:type :environment-variable
                                                            :name "GITHUB_TOKEN"}})
```

Requests use kebab-case maps; responses are Clojure maps, and list operations
collect all pages. This surface is beta and can change with the upstream SDK.

## Chat Completions

Use the Responses API for new OpenAI work. Chat Completions supports
OpenAI-compatible endpoints that do not support Responses. This includes local
LLMs and hosted compatible providers.

```clojure
(openai/create-chat-completion
 client
 {:model "gpt-4o-mini"
  :messages [{:role :system :content "Be terse."}
             {:role :user :content "Write one sentence about Clojure maps."}]})
;; => {:id "chatcmpl_..."
;;     :model "gpt-4o-mini"
;;     :created 1790000000
;;     :choices [{:index 0
;;                :finish-reason :stop
;;                :message {:role :assistant
;;                          :content "Clojure maps are ..."}}]
;;     :text "Clojure maps are ..."
;;     :usage {:prompt-tokens 14 :completion-tokens 12 :total-tokens 26}}
```

Function tools use the same JSON-schema-shaped `:parameters` maps as Responses:

```clojure
(openai/create-chat-completion
 client
 {:model "gpt-4o-mini"
  :messages [{:role :user :content "Weather in Denver?"}]
  :tools [{:type :function
           :name "get_weather"
           :description "Get current weather"
           :strict true
           :parameters {:type "object"
                        :properties {:location {:type "string"}}
                        :required ["location"]}}]
  :tool-choice {:type :function :name "get_weather"}})
```

Streaming returns the concatenated content. It calls the callback for each
normalized chunk:

```clojure
(openai/stream-chat-completion-text
 client
 {:model "gpt-4o-mini"
  :messages [{:role :user :content "Count to three."}]
  :stream-options {:include-usage true}}
 println)
```

## API namespaces

Service functions take an `openai.core/client` as the first argument. They
accept kebab-case request maps. Realtime WebSockets take a transport config map.

```clojure
(require '[openai.images :as images]
         '[openai.audio :as audio]
         '[openai.content-provenance-checks :as cpc]
         '[openai.moderations :as moderations]
         '[openai.safety :as safety]
         '[openai.completions :as completions]
         '[openai.vector-stores :as vector-stores]
         '[openai.uploads :as uploads]
         '[openai.containers :as containers]
         '[openai.conversations :as conversations]
         '[openai.fine-tuning :as fine-tuning]
         '[openai.evals :as evals]
         '[openai.skills :as skills]
         '[openai.videos :as videos]
         '[openai.chatkit :as chatkit]
         '[openai.beta.responses :as beta-responses]
         '[openai.beta.agents :as agents]
         '[openai.realtime :as realtime]
         '[openai.live :as live]
         '[openai.webhooks :as webhooks]
         '[openai.admin :as admin]
         '[openai.admin.projects :as admin-projects])

(images/generate client {:model "gpt-image-1" :prompt "A Clojure logo"})
(audio/create-speech client {:model "gpt-4o-mini-tts" :voice :alloy
                             :input "Hello"})
(cpc/create client {:file "image.png"})
(moderations/create client {:input "text"})
(safety/retrieve client "safety_alert_...")
(safety/case-retrieve client "case_...")
(completions/create client {:model "gpt-3.5-turbo-instruct" :prompt "Once"})
(vector-stores/create client {:name "docs" :file-ids ["file_..."]})
(uploads/create client {:filename "data.jsonl" :bytes 100
                        :mime-type "application/jsonl" :purpose :fine-tune})
(containers/create client {:name "sandbox"})
(conversations/create client {:items [{:role :user :content "Hello"}]})
(fine-tuning/create-job client {:model "gpt-4.1-mini"
                                :training-file "file_..."})
(evals/list client {:limit 20})
(skills/list client {:limit 20})
(videos/create client {:model "sora-2" :prompt "Ocean sunrise"
                       :size "1280x720" :seconds "8"})
(chatkit/create-session client {:workflow {:id "wf_123"} :user "user_42"})
(beta-responses/create-response client {:model "gpt-5" :input "Hello"})
(agents/create client {:model "gpt-5.1" :name "researcher"})
(live/live-create client {:session {:model "gpt-live-1"}
                          :transport {:sdp browser-sdp-offer}})
(webhooks/unwrap webhook-client raw-body request-headers)
(webhooks/create client {:name "primary" :url "https://example.test/webhooks"
                         :event-types [:response-completed]})
(webhooks/list client {:limit 20})
(webhooks/retrieve client "we_...")
(webhooks/update client "we_..." {:name "primary-v2"})
(webhooks/delete client "we_...")
(webhooks/rotate-secret client "we_..." {:keep-old-secret-active-for-24-hours true})
(webhooks/test-webhook-endpoint client "we_..." {:event-type :response-completed})
(webhooks/list-event-types client)
(admin/project-list admin-client {:limit 20})
(admin/external-storage-list admin-client {:project-id "proj_..."})
(admin-projects/service-account-list admin-client "proj_...")
```

Webhook endpoint management is available from `openai.webhooks`: `create`,
`list`, `retrieve`, `update`, `delete`, `rotate-secret`,
`test-webhook-endpoint`, and `list-event-types`. Endpoint `list` eagerly
returns all pages; the event-type endpoint returns its SDK-provided list.

`openai.core` also contains Responses, Chat Completions, embeddings, files,
batches, models, and stored Chat Completions. `openai.realtime` contains
WebSocket, session, client-secret, transcription, translation, and SIP call
helpers. `openai.live` contains WebRTC session creation and lifecycle helpers.
`openai.beta.agents` and its `sessions`, `sessions.turns`, `sessions.items`,
`sessions.events`, `sessions.artifacts`, `sessions.subagents` (and its nested
`items`/`turns`/`turns.items`), `environments` (and its nested `files` and
`templates`), and `vaults` (and its nested `credentials`) sub-namespaces cover
the beta multi-agent orchestration platform - see "Beta Agents API" above.
`openai.content-provenance-checks` contains Content Provenance Checks.
`openai.admin` includes organization external-storage configuration create,
retrieve, list, delete, and validation operations. `openai.safety` includes
safety alert and safety case retrieval.
`openai.graders` maps to the stable grader-model service. Model names are passed
through as strings, including `"gpt-6-astra"`. The service exposes
no operations in SDK 4.62.0.

List functions remain eager by default. Additive lazy siblings cover models,
files, batches, stored Chat Completions, response input items, vector stores and
their files/batches, and ChatKit threads/items. Their option maps accept
`:max-items` and `:max-pages` to bound realization.

The library wraps each non-deprecated operation that the Java SDK exposes. This
includes beta Agents, ChatKit, and Responses. The Assistants API (assistants/threads/runs) is not
wrapped because the SDK marks it as deprecated in favor of the Responses API.
Async clients, raw-response accessors, and per-call `RequestOptions` are
transport and accessor variants, not endpoints. The library does not duplicate them.
The managed Responses WebSocket connection (added in SDK 4.65.0) is likewise a
transport variant over an endpoint already covered via REST/SSE, and is not
wrapped.

## Running tests

```bash
clojure -M:test
```

Unit tests do not use a network. Skip `^:integration` tests, if added, without
`OPENAI_API_KEY`.

## License

Copyright © 2026 Savyasachi.

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
