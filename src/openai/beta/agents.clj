(ns openai.beta.agents
  "Clojure wrapper for the beta OpenAI Agents API."
  (:refer-clojure :exclude [list update])
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents Agent
                                                  AgentCreateParams
                                                  AgentCreateParams$Builder
                                                  AgentDeleteParams
                                                  AgentDeleteParams$Builder
                                                  AgentDeleted
                                                  AgentListParams
                                                  AgentListParams$Builder
                                                  AgentListParams$Order
                                                  AgentRetrieveParams
                                                  AgentRetrieveParams$Builder
                                                  AgentUpdateParams
                                                  AgentUpdateParams$Builder)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)))

(set! *warn-on-reflection* true)

(defn- wire-name ^String [^Object x]
  (if (keyword? x)
    (let [n (name x)
          ns (namespace x)]
      (str/replace (if ns (str ns "/" n) n) "-" "_"))
    x))

(defn- ->wire ^Object [^Object x]
  (walk/postwalk
   (fn [v]
     (cond
       (keyword? v) (wire-name v)
       (map-entry? v) v
       (map? v) (into {} (map (fn [[k value]] [(wire-name k) value])) v)
       :else v))
   x))

(defn- json-value ^JsonValue [^Object x]
  (JsonValue/from (->wire x)))

(defn- put-agent-create-properties! ^AgentCreateParams$Builder
  [^AgentCreateParams$Builder builder ^clojure.lang.IPersistentMap m]
  (doseq [[k v] m]
    (.putAdditionalBodyProperty builder (wire-name k) (json-value v)))
  builder)

(defn- ->agent-create-params ^AgentCreateParams
  [^clojure.lang.IPersistentMap req]
  (let [{:keys [model]} req]
    (when-not model (impl/missing-key! :model))
    (let [^String model-name (if (keyword? model) (name model) model)
          ^AgentCreateParams$Builder b (AgentCreateParams/builder)]
      (.model b model-name)
      (put-agent-create-properties! b (dissoc req :model))
      (.build b))))

(defn- agent->map ^clojure.lang.IPersistentMap [^Agent agent]
  (impl/sdk-object->clj agent))

(defn create
  "Create an agent from a kebab-case request map."
  ^clojure.lang.IPersistentMap
  [^OpenAIClient client ^clojure.lang.IPersistentMap req]
  (impl/with-api-errors
    (let [^BetaService beta (.beta client)
          ^AgentService svc (.agents beta)]
      (agent->map (.create svc (->agent-create-params req))))))

(defn- ->agent-retrieve-params ^AgentRetrieveParams [^String agent-id]
  (when-not agent-id (impl/missing-key! :agent-id))
  (let [^AgentRetrieveParams$Builder b (AgentRetrieveParams/builder)]
    (.agentId b agent-id)
    (.build b)))

(defn retrieve
  "Retrieve an agent by ID."
  ^clojure.lang.IPersistentMap
  [^OpenAIClient client ^String agent-id]
  (impl/with-api-errors
    (let [^BetaService beta (.beta client)
          ^AgentService svc (.agents beta)]
      (agent->map (.retrieve svc (->agent-retrieve-params agent-id))))))

(defn- put-agent-update-properties! ^AgentUpdateParams$Builder
  [^AgentUpdateParams$Builder builder ^clojure.lang.IPersistentMap m]
  (doseq [[k v] m]
    (.putAdditionalBodyProperty builder (wire-name k) (json-value v)))
  builder)

(defn- ->agent-update-params ^AgentUpdateParams
  [^String agent-id ^clojure.lang.IPersistentMap req]
  (when-not agent-id (impl/missing-key! :agent-id))
  (let [^AgentUpdateParams$Builder b (AgentUpdateParams/builder)]
    (.agentId b agent-id)
    (put-agent-update-properties! b req)
    (.build b)))

(defn update
  "Update an agent from a kebab-case request map."
  ^clojure.lang.IPersistentMap
  [^OpenAIClient client ^String agent-id ^clojure.lang.IPersistentMap req]
  (impl/with-api-errors
    (let [^BetaService beta (.beta client)
          ^AgentService svc (.agents beta)]
      (agent->map (.update svc (->agent-update-params agent-id req))))))

(defn- ->agent-delete-params ^AgentDeleteParams [^String agent-id]
  (when-not agent-id (impl/missing-key! :agent-id))
  (let [^AgentDeleteParams$Builder b (AgentDeleteParams/builder)]
    (.agentId b agent-id)
    (.build b)))

(defn- agent-deleted->map ^clojure.lang.IPersistentMap
  [^AgentDeleted deleted]
  (impl/sdk-object->clj deleted))

(defn delete
  "Delete an agent by ID."
  ^clojure.lang.IPersistentMap
  [^OpenAIClient client ^String agent-id]
  (impl/with-api-errors
    (let [^BetaService beta (.beta client)
          ^AgentService svc (.agents beta)]
      (agent-deleted->map (.delete svc (->agent-delete-params agent-id))))))

(defn- ->agent-list-params ^AgentListParams
  [^clojure.lang.IPersistentMap req]
  (let [^AgentListParams$Builder b (AgentListParams/builder)]
    (when-let [after (:after req)]
      (.after b ^String after))
    (when-let [limit (:limit req)]
      (.limit b (long limit)))
    (when-let [order (:order req)]
      (.order b (AgentListParams$Order/of (name order))))
    (.build b)))

(defn list
  "List agents."
  (^clojure.lang.IPersistentVector
   [^OpenAIClient client]
   (list client nil))
  (^clojure.lang.IPersistentVector
   [^OpenAIClient client ^clojure.lang.IPersistentMap req]
   (impl/with-api-errors
     (let [^BetaService beta (.beta client)
           ^AgentService svc (.agents beta)]
       (mapv agent->map (impl/all-pages (.list svc (->agent-list-params req))))))))
