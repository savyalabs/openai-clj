(ns openai.beta.agents.sessions.subagents-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents Subagent
                                           Subagent$Builder
                                           Subagent$Object
                                           Subagent$Status)
           (com.openai.models.beta.agents.sessions.subagents SubagentListPage
                                                              SubagentListPageResponse
                                                              SubagentListParams
                                                              SubagentRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions SubagentService)))

(set! *warn-on-reflection* true)

(def ^:private target-ns 'openai.beta.agents.sessions.subagents)

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

(defn- subagent ^Subagent [^String id ^Subagent$Status status]
  (let [^Subagent$Builder b (Subagent/builder)]
    (.id b id)
    (.closedAt b 15)
    (.addOutputTextInstruction b "Research carefully.")
    (.name b "researcher")
    (.object_ b Subagent$Object/AGENT_SESSION_SUBAGENT)
    (.openedAt b 11)
    (.parentAgentId b "agent_parent")
    (.sessionId b "sess_1")
    (.status b status)
    (.putAdditionalProperty b "custom_field" (JsonValue/from "preserved"))
    (.build b)))

(deftest validates-and-builds-subagent-params
  (let [retrieve-params (target-var '->subagent-retrieve-params)
        list-params (target-var '->subagent-list-params)]
    (is retrieve-params "subagent retrieve params builder is not implemented")
    (is list-params "subagent list params builder is not implemented")
    (when (and retrieve-params list-params)
      (testing "required fields"
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(retrieve-params nil "sub_1" {}))))
        (is (= {:openai/error :missing-key :key :subagent-id}
               (error-data #(retrieve-params "sess_1" nil {}))))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(list-params nil {})))))
      (testing "parameter conversion and additional query passthrough"
        (let [^SubagentRetrieveParams retrieve
              (retrieve-params "sess_1" "sub_1" {:trace-level :full-detail})
              ^SubagentListParams list
              (list-params "sess_1" {:after "sub_0"
                                     :limit 20
                                     :order :desc
                                     :trace-level :full-detail})]
          (is (= "sess_1" (.sessionId retrieve)))
          (is (= "sub_1" (opt (.subagentId retrieve))))
          (is (= ["full_detail"]
                 (.values (._additionalQueryParams retrieve) "trace_level")))
          (is (= "sess_1" (opt (.sessionId list))))
          (is (= "sub_0" (opt (.after list))))
          (is (= 20 (opt (.limit list))))
          (is (= "desc" (.asString (opt (.order list)))))
          (is (= ["full_detail"]
                 (.values (._additionalQueryParams list) "trace_level"))))))))

(deftest invokes-subagent-service-and-normalizes-responses
  (let [retrieve (target-var 'subagent-retrieve)
        list-subagents (target-var 'subagent-list)]
    (if (and retrieve list-subagents)
      (let [active (subagent "sub_1" Subagent$Status/ACTIVE)
            closed (subagent "sub_2" Subagent$Status/CLOSED)
            captured-retrieve (atom nil)
            captured-list (atom nil)
            page-holder (atom nil)
            empty-page-holder (atom nil)
            calls (atom 0)
            subagents (proxy [SubagentService] []
                        (retrieve [params]
                          (reset! captured-retrieve params)
                          active)
                        (list [params]
                          (let [call (swap! calls inc)]
                            (when (= 1 call) (reset! captured-list params))
                            (if (= 1 call)
                              @page-holder
                              @empty-page-holder))))]
        ;; Build the SDK page without bypassing AutoPager.
        (reset! page-holder
                (-> (SubagentListPage/builder)
                    (.service subagents)
                    (.params (-> (SubagentListParams/builder)
                                 (.sessionId "sess_1")
                                 (.build)))
                    (.response (-> (SubagentListPageResponse/builder)
                                   (.data [active closed])
                                   (.firstId "sub_1")
                                   (.hasMore false)
                                   (.lastId "sub_2")
                                   (.object_ (JsonValue/from "list"))
                                   (.build)))
                    (.build)))
        (reset! empty-page-holder
                (-> (SubagentListPage/builder)
                    (.service subagents)
                    (.params (-> (SubagentListParams/builder)
                                 (.sessionId "sess_1")
                                 (.build)))
                    (.response (-> (SubagentListPageResponse/builder)
                                   (.data [])
                                   (.firstId (java.util.Optional/empty))
                                   (.hasMore false)
                                   (.lastId (java.util.Optional/empty))
                                   (.object_ (JsonValue/from "list"))
                                   (.build)))
                    (.build)))
        (let [sessions (proxy [SessionService] [] (subagents [] subagents))
              agents (proxy [AgentService] [] (sessions [] sessions))
              beta (proxy [BetaService] [] (agents [] agents))
              client (proxy [OpenAIClient] [] (beta [] beta))
              expected {:id "sub_1"
                        :closed-at 15
                        :instructions [{:text "Research carefully."
                                        :type :output-text}]
                        :name "researcher"
                        :object :agent.session.subagent
                        :opened-at 11
                        :parent-agent-id "agent_parent"
                        :session-id "sess_1"
                        :status :active
                        :custom-field "preserved"}]
          (is (= expected (retrieve client "sess_1" "sub_1")))
          (is (= [(assoc expected :id "sub_1" :status :active)
                  (assoc expected :id "sub_2" :status :closed)]
                 (list-subagents client "sess_1" {:limit 2})))
          (is (= "sub_1"
                 (opt (.subagentId ^SubagentRetrieveParams @captured-retrieve))))
          (is (= 2 (opt (.limit ^SubagentListParams @captured-list))))))
      (is false "subagent service functions are not implemented"))))
