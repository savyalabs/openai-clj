(ns openai.beta.agents-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents Agent
                                                  Agent$Builder
                                                  Agent$Metadata
                                                  Agent$ServiceTier
                                                  AgentCreateParams
                                                  AgentDeleteParams
                                                  AgentDeleted
                                                  AgentListPage
                                                  AgentListPageResponse
                                                  AgentListPageResponse$Builder
                                                  AgentListParams
                                                  AgentListParams$Order
                                                  AgentRetrieveParams
                                                  AgentUpdateParams
                                                  AgentReasoning
                                                  AgentReasoning$Effort
                                                  AgentReasoning$Summary
                                                  AgentText
                                                  AgentText$Verbosity
                                                  MultiAgentConfig)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)))

(set! *warn-on-reflection* true)

(def mapper (json/object-mapper {:decode-key-fn true}))

(defn- json-value->clj [x]
  (json/read-value (json/write-value-as-string x) mapper))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- resolve-agents-var [sym]
  (try
    (requiring-resolve (symbol "openai.beta.agents" (name sym)))
    (catch java.io.FileNotFoundException _
      nil)))

(defn- agent-response ^Agent [^String id ^String instructions]
  (let [^Agent$Builder b (Agent/builder)]
    (.id b id)
    (.createdAt b 10)
    (.instructions b instructions)
    (.metadata b (-> (Agent$Metadata/builder) (.build)))
    (.model b "gpt-4o")
    (.multiAgent b (-> (MultiAgentConfig/builder)
                       (.enabled true)
                       (.maxConcurrentSubagents 3)
                       (.build)))
    (.name b "planner")
    (.object_ b (JsonValue/from "agent"))
    (.reasoning b (-> (AgentReasoning/builder)
                      (.effort AgentReasoning$Effort/LOW)
                      (.summary AgentReasoning$Summary/AUTO)
                      (.build)))
    (.serviceTier b Agent$ServiceTier/DEFAULT)
    (.text b (-> (AgentText/builder)
                 (.formatText)
                 (.verbosity AgentText$Verbosity/MEDIUM)
                 (.build)))
    (.tools b (java.util.ArrayList.))
    (.updatedAt b 11)
    (.build b)))

(deftest creates-agent-and-normalizes-response
  (let [build-params (resolve-agents-var '->agent-create-params)
        create-agent (resolve-agents-var 'create)]
    (if (and build-params create-agent)
      (let [request {:model "gpt-4o"
                     :name "planner"
                     :instructions "Plan carefully."
                     :multi-agent {:enabled true
                                   :max-concurrent-subagents 3}
                     :tools [{:type :web-search
                              :search-context-size :low}]}
            ^AgentCreateParams params (build-params request)
            response (agent-response "agent_1" "Plan carefully.")
            captured (atom nil)
            service (proxy [AgentService] []
                      (create [p] (reset! captured p) response))
            beta-service (proxy [BetaService] []
                           (agents [] service))
            client (proxy [OpenAIClient] []
                     (beta [] beta-service))]
        (testing "request parameters"
          (is (= "gpt-4o" (.model params)))
          (is (= "planner"
                 (-> params ._additionalBodyProperties
                     (get "name") json-value->clj)))
          (is (= "Plan carefully."
                 (-> params ._additionalBodyProperties
                     (get "instructions") json-value->clj)))
          (is (= {:enabled true :max_concurrent_subagents 3}
                 (-> params ._additionalBodyProperties
                     (get "multi_agent") json-value->clj)))
          (is (= [{:type "web_search" :search_context_size "low"}]
                 (-> params ._additionalBodyProperties
                     (get "tools") json-value->clj))))
        (testing "service call and response"
          (is (= {:id "agent_1"
                  :created-at 10
                  :instructions "Plan carefully."
                  :metadata {}
                  :model "gpt-4o"
                  :multi-agent {:enabled true :max-concurrent-subagents 3}
                  :name "planner"
                  :object "agent"
                  :reasoning {:effort "low" :summary "auto"}
                  :service-tier "default"
                  :text {:format {:type "text"} :verbosity "medium"}
                  :tools []
                  :updated-at 11}
                 (create-agent client request)))
          (is (= "gpt-4o" (.model ^AgentCreateParams @captured))))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :model}
                 (error-data #(build-params {}))))))
      (is false "openai.beta.agents/create is not implemented"))))

(deftest retrieves-agent-and-normalizes-response
  (let [build-params (resolve-agents-var '->agent-retrieve-params)
        retrieve-agent (resolve-agents-var 'retrieve)]
    (if (and build-params retrieve-agent)
      (let [^AgentRetrieveParams params (build-params "agent_1")
            response (agent-response "agent_1" "Plan carefully.")
            captured (atom nil)
            service (proxy [AgentService] []
                      (retrieve [p] (reset! captured p) response))
            beta-service (proxy [BetaService] []
                           (agents [] service))
            client (proxy [OpenAIClient] []
                     (beta [] beta-service))]
        (testing "request parameters"
          (is (= "agent_1" (.get (.agentId params)))))
        (testing "service call and response"
          (is (= {:id "agent_1"
                  :instructions "Plan carefully."
                  :model "gpt-4o"}
                 (select-keys (retrieve-agent client "agent_1")
                              [:id :instructions :model])))
          (is (= "agent_1"
                 (-> ^AgentRetrieveParams @captured .agentId .get))))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :agent-id}
                 (error-data #(build-params nil))))))
      (is false "openai.beta.agents/retrieve is not implemented"))))

(deftest updates-agent-and-normalizes-response
  (let [build-params (resolve-agents-var '->agent-update-params)
        update-agent (resolve-agents-var 'update)]
    (if (and build-params update-agent)
      (let [request {:instructions "Use the revised plan."
                     :reasoning {:effort :high :summary :detailed}}
            ^AgentUpdateParams params (build-params "agent_1" request)
            response (agent-response "agent_1" "Use the revised plan.")
            captured (atom nil)
            service (proxy [AgentService] []
                      (update [p] (reset! captured p) response))
            beta-service (proxy [BetaService] []
                           (agents [] service))
            client (proxy [OpenAIClient] []
                     (beta [] beta-service))]
        (testing "request parameters"
          (is (= "agent_1" (.get (.agentId params))))
          (is (= "Use the revised plan."
                 (-> params ._additionalBodyProperties
                     (get "instructions") json-value->clj)))
          (is (= {:effort "high" :summary "detailed"}
                 (-> params ._additionalBodyProperties
                     (get "reasoning") json-value->clj))))
        (testing "service call and response"
          (is (= {:id "agent_1" :instructions "Use the revised plan."}
                 (select-keys (update-agent client "agent_1" request)
                              [:id :instructions])))
          (is (= "agent_1"
                 (-> ^AgentUpdateParams @captured .agentId .get))))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :agent-id}
                 (error-data #(build-params nil request))))))
      (is false "openai.beta.agents/update is not implemented"))))

(deftest deletes-agent-and-normalizes-response
  (let [build-params (resolve-agents-var '->agent-delete-params)
        delete-agent (resolve-agents-var 'delete)]
    (if (and build-params delete-agent)
      (let [^AgentDeleteParams params (build-params "agent_1")
            response (-> (AgentDeleted/builder)
                         (.id "agent_1")
                         (.deleted true)
                         (.object_ (JsonValue/from "agent.deleted"))
                         (.build))
            captured (atom nil)
            service (proxy [AgentService] []
                      (delete [p] (reset! captured p) response))
            beta-service (proxy [BetaService] []
                           (agents [] service))
            client (proxy [OpenAIClient] []
                     (beta [] beta-service))]
        (testing "request parameters"
          (is (= "agent_1" (.get (.agentId params)))))
        (testing "service call and response"
          (is (= {:id "agent_1" :deleted true :object "agent.deleted"}
                 (delete-agent client "agent_1")))
          (is (= "agent_1"
                 (-> ^AgentDeleteParams @captured .agentId .get))))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :agent-id}
                 (error-data #(build-params nil))))))
      (is false "openai.beta.agents/delete is not implemented"))))

(deftest lists-all-agents-and-normalizes-responses
  (let [build-params (resolve-agents-var '->agent-list-params)
        list-agents (resolve-agents-var 'list)]
    (if (and build-params list-agents)
      (let [^AgentListParams params
            (build-params {:after "agent_0" :limit 2 :order :desc})
            first-agent (agent-response "agent_1" "Plan carefully.")
            second-agent (agent-response "agent_2" "Execute carefully.")
            ^AgentListPageResponse$Builder response-builder
            (AgentListPageResponse/builder)
            _ (.data response-builder
                     (java.util.ArrayList.
                      ^java.util.Collection
                      (vector first-agent second-agent)))
            _ (.firstId response-builder "agent_1")
            _ (.hasMore response-builder false)
            _ (.lastId response-builder "agent_2")
            _ (.object_ response-builder (JsonValue/from "list"))
            page-response (.build response-builder)
            empty-page-response (-> (AgentListPageResponse/builder)
                                    (.data (java.util.ArrayList.))
                                    (.firstId "")
                                    (.hasMore false)
                                    (.lastId "")
                                    (.object_ (JsonValue/from "list"))
                                    (.build))
            captured (atom nil)
            page-ref (atom nil)
            empty-page-ref (atom nil)
            service (proxy [AgentService] []
                      (list [p]
                        (if (= "agent_2"
                               (.orElse (.after ^AgentListParams p) nil))
                          @empty-page-ref
                          (do
                            (reset! captured p)
                            @page-ref))))
            page (-> (AgentListPage/builder)
                     (.service service)
                     (.params params)
                     (.response page-response)
                     (.build))
            empty-page (-> (AgentListPage/builder)
                           (.service service)
                           (.params params)
                           (.response empty-page-response)
                           (.build))
            beta-service (proxy [BetaService] []
                           (agents [] service))
            client (proxy [OpenAIClient] []
                     (beta [] beta-service))]
        (reset! page-ref page)
        (reset! empty-page-ref empty-page)
        (testing "request parameters"
          (is (= "agent_0" (.get (.after params))))
          (is (= 2 (.get (.limit params))))
          (is (= "desc"
                 (.asString ^AgentListParams$Order (.get (.order params))))))
        (testing "optional params and normalized pagination"
          (is (= ["agent_1" "agent_2"]
                 (mapv :id (list-agents client))))
          (is (false? (.isPresent (.after ^AgentListParams @captured))))
          (is (= ["agent_1" "agent_2"]
                 (mapv :id (list-agents client
                                       {:after "agent_0"
                                        :limit 2
                                        :order :desc}))))
          (is (= "agent_0"
                 (-> ^AgentListParams @captured .after .get)))
          (is (= 2 (-> ^AgentListParams @captured .limit .get)))
          (is (= "desc"
                 (.asString
                  ^AgentListParams$Order
                  (cast AgentListParams$Order
                        (-> ^AgentListParams @captured .order .get)))))))
      (is false "openai.beta.agents/list is not implemented"))))
