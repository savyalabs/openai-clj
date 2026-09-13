(ns openai.beta.agents.sessions.turns
  "Clojure wrapper for beta Agent Session Turn operations."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents.sessions.turns Turn
                                                               TurnListPage
                                                               TurnListParams
                                                               TurnListParams$Builder
                                                               TurnListParams$Order
                                                               TurnRetrieveParams
                                                               TurnRetrieveParams$Builder)
           (com.openai.services.blocking.beta.agents.sessions TurnService)))

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

(defn- turn->map [^Turn turn]
  (normalize-value (impl/sdk-object->clj turn)))

(defn- ->turn-retrieve-params ^TurnRetrieveParams [session-id turn-id]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not turn-id (impl/missing-key! :turn-id))
  (let [^TurnRetrieveParams$Builder b (TurnRetrieveParams/builder)]
    (.sessionId b ^String session-id)
    (.turnId b ^String turn-id)
    (.build b)))

(defn- ->turn-list-params ^TurnListParams
  [session-id {:keys [after limit order]}]
  (when-not session-id (impl/missing-key! :session-id))
  (let [^TurnListParams$Builder b (TurnListParams/builder)]
    (.sessionId b ^String session-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (TurnListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn retrieve-turn
  "Retrieve a turn from an Agent Session."
  [^OpenAIClient client session-id turn-id]
  (impl/with-api-errors
    (let [params (->turn-retrieve-params session-id turn-id)
          ^TurnService service (.. client (beta) (agents) (sessions) (turns))]
      (turn->map (.retrieve service params)))))

(defn list-turns
  "List all turns in an Agent Session, following SDK pagination."
  ([^OpenAIClient client session-id]
   (list-turns client session-id {}))
  ([^OpenAIClient client session-id opts]
   (impl/with-api-errors
     (let [params (->turn-list-params session-id opts)
           ^TurnService service (.. client (beta) (agents) (sessions) (turns))
           ^TurnListPage page (.list service params)]
       (mapv turn->map (impl/all-pages page))))))
