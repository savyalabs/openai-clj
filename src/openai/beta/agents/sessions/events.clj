(ns openai.beta.agents.sessions.events
  "Clojure wrapper for beta Agent Session event creation and streaming.

  EventService exposes two operations in openai-java 4.63.1: `create`, which
  accepts a session ID, idempotency key, and AgentSessionInputParam payloads
  and returns void; and `streamStreaming`, which accepts a session ID and
  returns an SSE StreamResponse of AgentSessionEvent payloads."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.core.http StreamResponse)
           (com.openai.models.beta.agents AgentSessionEvent AgentSessionInputParam)
           (com.openai.models.beta.agents.sessions.events EventCreateParams
                                                           EventCreateParams$Builder
                                                           EventStreamParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions EventService)))

(set! *warn-on-reflection* true)

(defn- wire-name ^String [x]
  (if (keyword? x)
    (let [n (name x)
          ns (namespace x)]
      (str/replace (if ns (str ns "/" n) n) "-" "_"))
    (str x)))

(defn- ->wire [x]
  (walk/postwalk
   (fn [v]
     (cond
       (keyword? v) (wire-name v)
       (map-entry? v) v
       (map? v) (into {} (map (fn [[k value]] [(wire-name k) value])) v)
       :else v))
   x))

(defn- json-value ^JsonValue [x]
  (JsonValue/from (->wire x)))

(defn- event-service ^EventService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)
        ^SessionService sessions (.sessions agents)]
    (.events sessions)))

(defn ->create-params ^EventCreateParams
  [^String session-id {:keys [events idempotency-key] :as req}]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not events (impl/missing-key! :events))
  (let [^EventCreateParams$Builder b (EventCreateParams/builder)]
    (.sessionId b session-id)
    (.events b ^java.util.List
             (mapv #(impl/sdk-input-object % AgentSessionInputParam) events))
    (when idempotency-key (.idempotencyKey b ^String idempotency-key))
    (doseq [[k v] (dissoc req :events :idempotency-key)]
      (.putAdditionalBodyProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn ->stream-params ^EventStreamParams [^String session-id]
  (when-not session-id (impl/missing-key! :session-id))
  (-> (EventStreamParams/builder)
      (.sessionId session-id)
      (.build)))

(defn- event->map ^clojure.lang.IPersistentMap [^AgentSessionEvent event]
  (let [m (impl/sdk-object->clj event)]
    (cond-> m
      (string? (:type m)) (update :type impl/->keyword))))

(defn create
  "Create input events for an Agent Session.

  `req` requires `:events`, accepts `:idempotency-key`, and passes remaining
  keys through as additional request-body properties."
  [^OpenAIClient client ^String session-id req]
  (impl/with-api-errors
    (.create (event-service client) (->create-params session-id req))
    nil))

(defn stream
  "Consume an Agent Session SSE stream and invoke `on-event` with each event
  as a kebab-case Clojure map."
  [^OpenAIClient client ^String session-id on-event]
  (impl/with-api-errors
    (with-open [^StreamResponse response
                (.streamStreaming (event-service client)
                                  (->stream-params session-id))]
      (doseq [^AgentSessionEvent event
              (iterator-seq (.iterator (.stream response)))]
        (when on-event
          (on-event (event->map event)))))
    nil))
