(ns openai.beta.agents.sessions.items
  "Clojure wrapper for beta Agent Session Item operations."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents AgentSessionItem)
           (com.openai.models.beta.agents.sessions.items ItemListPage
                                                          ItemListParams
                                                          ItemListParams$Builder
                                                          ItemListParams$Order)
           (com.openai.services.blocking.beta.agents.sessions ItemService)))

(set! *warn-on-reflection* true)

(defn- normalize-value [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [k (if (and (#{:type :status :role} k) (string? v))
                      (impl/->keyword v)
                      (normalize-value v))]))
          value)

    (vector? value) (mapv normalize-value value)
    :else value))

(defn- item->map [^AgentSessionItem item]
  (normalize-value (impl/sdk-object->clj item)))

(defn- ->item-list-params ^ItemListParams
  [session-id {:keys [after limit order]}]
  (when-not session-id (impl/missing-key! :session-id))
  (let [^ItemListParams$Builder b (ItemListParams/builder)]
    (.sessionId b ^String session-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (ItemListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn list-items
  "List all items in an Agent Session, following SDK pagination."
  ([^OpenAIClient client session-id]
   (list-items client session-id {}))
  ([^OpenAIClient client session-id opts]
   (impl/with-api-errors
     (let [params (->item-list-params session-id opts)
           ^ItemService service (.. client (beta) (agents) (sessions) (items))
           ^ItemListPage page (.list service params)]
       (mapv item->map (impl/all-pages page))))))
