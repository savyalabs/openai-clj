(ns openai.beta.agents.sessions.items-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.errors OpenAIIoException)
           (com.openai.models.beta.agents AgentOutputItemStatus
                                           AgentSessionItem
                                           AgentSessionMessage
                                           AgentSessionMessage$Builder
                                           AgentSessionMessage$Role)
           (com.openai.models.beta.agents.sessions.items ItemListPage
                                                          ItemListPageResponse
                                                          ItemListPageResponse$Builder
                                                          ItemListParams$Order
                                                          ItemListParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions ItemService)))

(set! *warn-on-reflection* true)

(defn- api-var [sym]
  (try
    (require 'openai.beta.agents.sessions.items)
    (ns-resolve 'openai.beta.agents.sessions.items sym)
    (catch java.io.FileNotFoundException _
      nil)))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- message-item [id text]
  (let [^AgentSessionMessage$Builder b (AgentSessionMessage/builder)
        message (do
                  (.id b ^String id)
                  (.addInputTextContent b ^String text)
                  (.phase b ^java.util.Optional (java.util.Optional/empty))
                  (.role b AgentSessionMessage$Role/USER)
                  (.status b AgentOutputItemStatus/COMPLETED)
                  (.turnId b "turn_1")
                  (.putAdditionalProperty b "future_field" (JsonValue/from 7))
                  (.build b))]
    (AgentSessionItem/ofMessage message)))

(defn- item-page
  [^ItemService service ^ItemListParams params items has-more]
  (let [^AgentSessionItem first-item (first items)
        ^AgentSessionItem last-item (last items)
        ^ItemListPageResponse$Builder b (ItemListPageResponse/builder)
        response (do
                   (.data b ^java.util.List items)
                   (.firstId b (if first-item
                                 (-> first-item .asMessage .id impl/opt-get)
                                 (java.util.Optional/empty)))
                   (.hasMore b (boolean has-more))
                   (.lastId b (if last-item
                                (-> last-item .asMessage .id impl/opt-get)
                                (java.util.Optional/empty)))
                   (.build b))]
    (-> (ItemListPage/builder)
        (.service service)
        (.params params)
        (.response response)
        (.build))))

(defn- client-with-items [^ItemService items]
  (let [sessions (proxy [SessionService] []
                   (items [] items))
        agents (proxy [AgentService] []
                 (sessions [] sessions))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(deftest builds-item-list-parameters
  (let [list-params (api-var '->item-list-params)]
    (if list-params
      (let [^ItemListParams params
            (list-params "sess_1" {:after "item_0" :limit 50 :order :desc})]
        (is (= "sess_1" (impl/opt-get (.sessionId params))))
        (is (= "item_0" (impl/opt-get (.after params))))
        (is (= 50 (impl/opt-get (.limit params))))
        (is (= "desc" (.asString ^ItemListParams$Order
                                 (impl/opt-get (.order params)))))
        (testing "session ID is required"
          (is (= {:openai/error :missing-key :key :session-id}
                 (error-data #(list-params nil {}))))))
      (is false "Agent Session Items parameter builder is not implemented"))))

(deftest lists-all-item-pages-and-normalizes-responses
  (let [list-items (api-var 'list-items)]
    (if list-items
      (let [captured (atom [])
            service-ref (atom nil)
            calls (atom 0)
            service (proxy [ItemService] []
                      (list [params]
                        (swap! captured conj params)
                        (let [^ItemListParams params params]
                          (case (swap! calls inc)
                            2 (item-page @service-ref params
                                         [(message-item "item_2" "second")] false)
                            3 (item-page @service-ref params [] false)
                            (item-page @service-ref params
                                       [(message-item "item_1" "first")] true)))))
            _ (reset! service-ref service)
            client (client-with-items service)
            result (list-items client "sess_1" {:limit 1 :order :asc})]
        (is (= ["item_1" "item_2"] (mapv :id result)))
        (is (= [:message :message] (mapv :type result)))
        (is (= [:user :user] (mapv :role result)))
        (is (= [:completed :completed] (mapv :status result)))
        (is (= [:input-text :input-text]
               (mapv #(get-in % [:content 0 :type]) result)))
        (is (= [7 7] (mapv :future-field result)))
        (is (= [nil "item_1" "item_2"]
               (mapv #(impl/opt-get (.after ^ItemListParams %)) @captured)))
        (is (= [1 1 1]
               (mapv #(impl/opt-get (.limit ^ItemListParams %)) @captured))))
      (is false "openai.beta.agents.sessions.items/list-items is not implemented"))))

(deftest normalizes-item-api-errors
  (let [list-items (api-var 'list-items)]
    (if list-items
      (let [service (proxy [ItemService] []
                      (list [_]
                        (throw (OpenAIIoException. "offline"))))
            client (client-with-items service)]
        (is (= {:openai/error :io-error}
               (error-data #(list-items client "sess_1")))))
      (is false "openai.beta.agents.sessions.items/list-items is not implemented"))))
