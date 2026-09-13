(ns openai.beta.agents.sessions.subagents.turns-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.sessions.subagents.turns TurnListPage
                                                                  TurnListPageResponse
                                                                  TurnListParams
                                                                  TurnRetrieveParams)
           (com.openai.models.beta.agents.sessions.turns Turn
                                                         Turn$Builder
                                                         Turn$Object
                                                         Turn$Status)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions SubagentService)
           (com.openai.services.blocking.beta.agents.sessions.subagents TurnService)))

(set! *warn-on-reflection* true)

(def ^:private target-ns 'openai.beta.agents.sessions.subagents.turns)

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

(defn- turn ^Turn [^String id ^Turn$Status status]
  (let [^Turn$Builder b (Turn/builder)]
    (.id b id)
    (.agentId b "agent_child")
    (.completedAt b 14)
    (.createdAt b 12)
    (.error b ^java.util.Optional (java.util.Optional/empty))
    (.object_ b Turn$Object/AGENT_SESSION_TURN)
    (.sessionId b "sess_1")
    (.startedAt b 13)
    (.status b status)
    (.subagentId b "sub_1")
    (.usage b ^java.util.Optional (java.util.Optional/empty))
    (.putAdditionalProperty b "custom_field" (JsonValue/from "preserved"))
    (.build b)))

(deftest validates-and-builds-subagent-turn-params
  (let [retrieve-params (target-var '->turn-retrieve-params)
        list-params (target-var '->turn-list-params)]
    (is retrieve-params "turn retrieve params builder is not implemented")
    (is list-params "turn list params builder is not implemented")
    (when (and retrieve-params list-params)
      (testing "required fields"
        (doseq [[args key]
                [[[nil "sub_1" "turn_1" {}] :session-id]
                 [["sess_1" nil "turn_1" {}] :subagent-id]
                 [["sess_1" "sub_1" nil {}] :turn-id]]]
          (is (= {:openai/error :missing-key :key key}
                 (error-data #(apply retrieve-params args)))))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(list-params nil "sub_1" {}))))
        (is (= {:openai/error :missing-key :key :subagent-id}
               (error-data #(list-params "sess_1" nil {})))))
      (testing "parameter conversion and additional query passthrough"
        (let [^TurnRetrieveParams retrieve
              (retrieve-params "sess_1" "sub_1" "turn_1"
                               {:trace-level :full-detail})
              ^TurnListParams list
              (list-params "sess_1" "sub_1"
                           {:after "turn_0" :limit 30 :order :desc
                            :trace-level :full-detail})]
          (is (= "sess_1" (.sessionId retrieve)))
          (is (= "sub_1" (.subagentId retrieve)))
          (is (= "turn_1" (opt (.turnId retrieve))))
          (is (= ["full_detail"]
                 (.values (._additionalQueryParams retrieve) "trace_level")))
          (is (= "sess_1" (.sessionId list)))
          (is (= "sub_1" (opt (.subagentId list))))
          (is (= "turn_0" (opt (.after list))))
          (is (= 30 (opt (.limit list))))
          (is (= "desc" (.asString (opt (.order list)))))
          (is (= ["full_detail"]
                 (.values (._additionalQueryParams list) "trace_level"))))))))

(deftest invokes-subagent-turn-service-and-normalizes-responses
  (let [retrieve (target-var 'turn-retrieve)
        list-turns (target-var 'turn-list)]
    (if (and retrieve list-turns)
      (let [running (turn "turn_1" Turn$Status/IN_PROGRESS)
            completed (turn "turn_2" Turn$Status/COMPLETED)
            captured-retrieve (atom nil)
            captured-list (atom nil)
            page-holder (atom nil)
            turns (proxy [TurnService] []
                    (retrieve [params]
                      (reset! captured-retrieve params)
                      running)
                    (list [params]
                      (reset! captured-list params)
                      @page-holder))
            _ (reset! page-holder
                      (-> (TurnListPage/builder)
                          (.service turns)
                          (.params (-> (TurnListParams/builder)
                                       (.sessionId "sess_1")
                                       (.subagentId "sub_1")
                                       (.build)))
                          (.response (-> (TurnListPageResponse/builder)
                                         (.data [running completed])
                                         (.firstId "turn_1")
                                         (.hasMore false)
                                         (.lastId "turn_2")
                                         (.object_ (JsonValue/from "list"))
                                         (.build)))
                          (.build)))
            subagents (proxy [SubagentService] [] (turns [] turns))
            sessions (proxy [SessionService] [] (subagents [] subagents))
            agents (proxy [AgentService] [] (sessions [] sessions))
            beta (proxy [BetaService] [] (agents [] agents))
            client (proxy [OpenAIClient] [] (beta [] beta))
            expected {:id "turn_1"
                      :agent-id "agent_child"
                      :completed-at 14
                      :created-at 12
                      :error nil
                      :object :agent.session.turn
                      :session-id "sess_1"
                      :started-at 13
                      :status :in-progress
                      :subagent-id "sub_1"
                      :usage nil
                      :custom-field "preserved"}]
        (is (= expected
               (retrieve client "sess_1" "sub_1" "turn_1")))
        (is (= [(assoc expected :id "turn_1" :status :in-progress)
                (assoc expected :id "turn_2" :status :completed)]
               (list-turns client "sess_1" "sub_1" {:limit 2})))
        (is (= "turn_1"
               (opt (.turnId ^TurnRetrieveParams @captured-retrieve))))
        (is (= 2 (opt (.limit ^TurnListParams @captured-list)))))
      (is false "subagent turn service functions are not implemented"))))
