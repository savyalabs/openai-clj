(ns openai.beta.agents.sessions.subagents.items-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents AgentSessionItem
                                           AgentSessionItem$AgentMessage)
           (com.openai.models.beta.agents.sessions.subagents.items ItemListPage
                                                                    ItemListPageResponse
                                                                    ItemListParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions SubagentService)
           (com.openai.services.blocking.beta.agents.sessions.subagents ItemService)))

(set! *warn-on-reflection* true)

(def ^:private target-ns 'openai.beta.agents.sessions.subagents.items)

(defn- target-var [sym]
  (try
    (require target-ns)
    (ns-resolve target-ns sym)
    (catch java.io.FileNotFoundException _ nil)))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- opt [^java.util.Optional value]
  (when (.isPresent value) (.get value)))

(defn- agent-message ^AgentSessionItem []
  (AgentSessionItem/ofAgentMessage
   (-> (AgentSessionItem$AgentMessage/builder)
       (.id "item_1")
       (.addOutputTextContent "hello")
       (.recipientAgentId "agent_parent")
       (.senderAgentId "agent_child")
       (.turnId "turn_1")
       (.type (JsonValue/from "agent_message"))
       (.build))))

(deftest validates-and-builds-subagent-item-list-params
  (let [build-params (target-var '->item-list-params)]
    (is build-params "subagent item list params builder is not implemented")
    (when build-params
      (testing "required fields"
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(build-params nil "sub_1" {}))))
        (is (= {:openai/error :missing-key :key :subagent-id}
               (error-data #(build-params "sess_1" nil {})))))
      (testing "parameter conversion and additional query passthrough"
        (let [^ItemListParams params
              (build-params "sess_1" "sub_1"
                            {:after "item_0" :limit 25 :order :asc
                             :trace-level :full-detail})]
          (is (= "sess_1" (.sessionId params)))
          (is (= "sub_1" (opt (.subagentId params))))
          (is (= "item_0" (opt (.after params))))
          (is (= 25 (opt (.limit params))))
          (is (= "asc" (.asString (opt (.order params)))))
          (is (= ["full_detail"]
                 (.values (._additionalQueryParams params) "trace_level"))))))))

(deftest invokes-subagent-item-service-and-normalizes-items
  (let [item-list (target-var 'item-list)]
    (if item-list
      (let [item (agent-message)
            captured (atom nil)
            page-holder (atom nil)
            empty-page-holder (atom nil)
            calls (atom 0)
            items (proxy [ItemService] []
                    (list [params]
                      (let [call (swap! calls inc)]
                        (when (= 1 call) (reset! captured params))
                        (if (= 1 call)
                          @page-holder
                          @empty-page-holder))))
            _ (reset! page-holder
                      (-> (ItemListPage/builder)
                          (.service items)
                          (.params (-> (ItemListParams/builder)
                                       (.sessionId "sess_1")
                                       (.subagentId "sub_1")
                                       (.build)))
                          (.response (-> (ItemListPageResponse/builder)
                                         (.data [item])
                                         (.firstId "item_1")
                                         (.hasMore false)
                                         (.lastId "item_1")
                                         (.object_ (JsonValue/from "list"))
                                         (.build)))
                          (.build)))
            _ (reset! empty-page-holder
                      (-> (ItemListPage/builder)
                          (.service items)
                          (.params (-> (ItemListParams/builder)
                                       (.sessionId "sess_1")
                                       (.subagentId "sub_1")
                                       (.build)))
                          (.response (-> (ItemListPageResponse/builder)
                                         (.data [])
                                         (.firstId (java.util.Optional/empty))
                                         (.hasMore false)
                                         (.lastId (java.util.Optional/empty))
                                         (.object_ (JsonValue/from "list"))
                                         (.build)))
                          (.build)))
            subagents (proxy [SubagentService] [] (items [] items))
            sessions (proxy [SessionService] [] (subagents [] subagents))
            agents (proxy [AgentService] [] (sessions [] sessions))
            beta (proxy [BetaService] [] (agents [] agents))
            client (proxy [OpenAIClient] [] (beta [] beta))]
        (is (= [{:id "item_1"
                 :content [{:text "hello" :type :output-text}]
                 :recipient-agent-id "agent_parent"
                 :sender-agent-id "agent_child"
                 :turn-id "turn_1"
                 :type :agent-message}]
               (item-list client "sess_1" "sub_1" {:limit 1})))
        (is (= 1 (opt (.limit ^ItemListParams @captured)))))
      (is false "subagent item-list is not implemented"))))
