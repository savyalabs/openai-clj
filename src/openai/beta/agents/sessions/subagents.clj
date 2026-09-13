(ns openai.beta.agents.sessions.subagents
  "Clojure wrapper for beta Agent Session Subagent operations."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents Subagent)
           (com.openai.models.beta.agents.sessions.subagents SubagentListPage
                                                              SubagentListParams
                                                              SubagentListParams$Builder
                                                              SubagentListParams$Order
                                                              SubagentRetrieveParams
                                                              SubagentRetrieveParams$Builder)
           (com.openai.services.blocking.beta.agents.sessions SubagentService)))

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

(defn- subagent->map [^Subagent subagent]
  (normalize-value (impl/sdk-object->clj subagent)))

(defn- query-value [value]
  (if (keyword? value) (impl/enum-name value) (str value)))

(defn- ->subagent-retrieve-params ^SubagentRetrieveParams
  [session-id subagent-id params]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not subagent-id (impl/missing-key! :subagent-id))
  (let [^SubagentRetrieveParams$Builder b (SubagentRetrieveParams/builder)]
    (.sessionId b ^String session-id)
    (.subagentId b ^String subagent-id)
    (doseq [[k v] params]
      (.putAdditionalQueryParam b (impl/enum-name k) (query-value v)))
    (.build b)))

(defn- ->subagent-list-params ^SubagentListParams
  [session-id {:keys [after limit order] :as params}]
  (when-not session-id (impl/missing-key! :session-id))
  (let [^SubagentListParams$Builder b (SubagentListParams/builder)]
    (.sessionId b ^String session-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (SubagentListParams$Order/of (impl/enum-name order))))
    (doseq [[k v] (dissoc params :after :limit :order)]
      (.putAdditionalQueryParam b (impl/enum-name k) (query-value v)))
    (.build b)))

(defn- subagents-service ^SubagentService [^OpenAIClient client]
  (.. client (beta) (agents) (sessions) (subagents)))

(defn subagent-retrieve
  "Retrieve a subagent from an Agent Session."
  [^OpenAIClient client session-id subagent-id]
  (impl/with-api-errors
    (let [^SubagentService service (subagents-service client)
          ^Subagent response
          (.retrieve service
                     (->subagent-retrieve-params session-id subagent-id {}))]
      (subagent->map response))))

(defn subagent-list
  "List all subagents in an Agent Session, following SDK pagination."
  ([^OpenAIClient client session-id]
   (subagent-list client session-id {}))
  ([^OpenAIClient client session-id params]
   (impl/with-api-errors
     (let [^SubagentService service (subagents-service client)
           ^SubagentListPage page
           (.list service (->subagent-list-params session-id params))]
       (mapv subagent->map (impl/all-pages page))))))
