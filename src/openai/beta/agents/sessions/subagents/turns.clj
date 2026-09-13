(ns openai.beta.agents.sessions.subagents.turns
  "Clojure wrapper for beta Agent Session Subagent Turn operations."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents.sessions.subagents.turns TurnListPage
                                                                  TurnListParams
                                                                  TurnListParams$Builder
                                                                  TurnListParams$Order
                                                                  TurnRetrieveParams
                                                                  TurnRetrieveParams$Builder)
           (com.openai.models.beta.agents.sessions.turns Turn)
           (com.openai.services.blocking.beta.agents.sessions.subagents TurnService)))

(set! *warn-on-reflection* true)

(defn- normalize-value [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [k (if (and (#{:object :role :status :type} k) (string? v))
                      (impl/->keyword v)
                      (normalize-value v))]))
          value)

    (vector? value) (mapv normalize-value value)
    :else value))

(defn- turn->map [^Turn turn]
  (normalize-value (impl/sdk-object->clj turn)))

(defn- query-value [value]
  (if (keyword? value) (impl/enum-name value) (str value)))

(defn- ->turn-retrieve-params ^TurnRetrieveParams
  [session-id subagent-id turn-id params]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not subagent-id (impl/missing-key! :subagent-id))
  (when-not turn-id (impl/missing-key! :turn-id))
  (let [^TurnRetrieveParams$Builder b (TurnRetrieveParams/builder)]
    (.sessionId b ^String session-id)
    (.subagentId b ^String subagent-id)
    (.turnId b ^String turn-id)
    (doseq [[k v] params]
      (.putAdditionalQueryParam b (impl/enum-name k) (query-value v)))
    (.build b)))

(defn- ->turn-list-params ^TurnListParams
  [session-id subagent-id {:keys [after limit order] :as params}]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not subagent-id (impl/missing-key! :subagent-id))
  (let [^TurnListParams$Builder b (TurnListParams/builder)]
    (.sessionId b ^String session-id)
    (.subagentId b ^String subagent-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (TurnListParams$Order/of (impl/enum-name order))))
    (doseq [[k v] (dissoc params :after :limit :order)]
      (.putAdditionalQueryParam b (impl/enum-name k) (query-value v)))
    (.build b)))

(defn- turns-service ^TurnService [^OpenAIClient client]
  (.. client (beta) (agents) (sessions) (subagents) (turns)))

(defn turn-retrieve
  "Retrieve a turn from an Agent Session subagent."
  [^OpenAIClient client session-id subagent-id turn-id]
  (impl/with-api-errors
    (let [^TurnService service (turns-service client)
          ^Turn response
          (.retrieve service
                     (->turn-retrieve-params session-id subagent-id turn-id {}))]
      (turn->map response))))

(defn turn-list
  "List all turns for a subagent, following SDK pagination."
  ([^OpenAIClient client session-id subagent-id]
   (turn-list client session-id subagent-id {}))
  ([^OpenAIClient client session-id subagent-id params]
   (impl/with-api-errors
     (let [^TurnService service (turns-service client)
           ^TurnListPage page
           (.list service (->turn-list-params session-id subagent-id params))]
       (mapv turn->map (impl/all-pages page))))))
