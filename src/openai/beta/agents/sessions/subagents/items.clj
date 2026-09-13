(ns openai.beta.agents.sessions.subagents.items
  "Clojure wrapper for beta Agent Session Subagent Item operations."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents AgentSessionItem)
           (com.openai.models.beta.agents.sessions.subagents.items ItemListPage
                                                                    ItemListParams
                                                                    ItemListParams$Builder
                                                                    ItemListParams$Order)
           (com.openai.services.blocking.beta.agents.sessions.subagents ItemService)))

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

(defn- item->map [^AgentSessionItem item]
  (normalize-value (impl/sdk-object->clj item)))

(defn- query-value [value]
  (if (keyword? value) (impl/enum-name value) (str value)))

(defn- put-additional-query-params!
  [^ItemListParams$Builder builder params]
  (doseq [[k v] (dissoc params :after :limit :order)]
    (.putAdditionalQueryParam builder (impl/enum-name k) (query-value v)))
  builder)

(defn- ->item-list-params ^ItemListParams
  [session-id subagent-id {:keys [after limit order] :as params}]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not subagent-id (impl/missing-key! :subagent-id))
  (let [^ItemListParams$Builder b (ItemListParams/builder)]
    (.sessionId b ^String session-id)
    (.subagentId b ^String subagent-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (ItemListParams$Order/of (impl/enum-name order))))
    (put-additional-query-params! b params)
    (.build b)))

(defn item-list
  "List all items for a subagent, following SDK pagination."
  ([^OpenAIClient client session-id subagent-id]
   (item-list client session-id subagent-id {}))
  ([^OpenAIClient client session-id subagent-id params]
   (impl/with-api-errors
     (let [^ItemService service
           (.. client (beta) (agents) (sessions) (subagents) (items))
           ^ItemListPage page
           (.list service (->item-list-params session-id subagent-id params))]
       (mapv item->map (impl/all-pages page))))))
