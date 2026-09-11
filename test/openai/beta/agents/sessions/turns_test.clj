(ns openai.beta.agents.sessions.turns-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.errors OpenAIIoException)
           (com.openai.models.beta.agents.sessions.turns Turn
                                                               Turn$Builder
                                                               Turn$Object
                                                               Turn$Status
                                                               TurnListPage
                                                               TurnListPageResponse
                                                               TurnListPageResponse$Builder
                                                               TurnListParams
                                                               TurnListParams$Order
                                                               TurnRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions TurnService)))

(set! *warn-on-reflection* true)

(defn- api-var [sym]
  (try
    (require 'openai.beta.agents.sessions.turns)
    (ns-resolve 'openai.beta.agents.sessions.turns sym)
    (catch java.io.FileNotFoundException _
      nil)))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- turn [id status]
  (let [^java.util.Optional empty (java.util.Optional/empty)
        ^Turn$Builder b (Turn/builder)]
    (.id b ^String id)
    (.agentId b "agent_1")
    (.completedAt b empty)
    (.createdAt b 123)
    (.error b empty)
    (.object_ b Turn$Object/AGENT_SESSION_TURN)
    (.sessionId b "sess_1")
    (.startedAt b empty)
    (.status b ^Turn$Status status)
    (.subagentId b empty)
    (.usage b empty)
    (.putAdditionalProperty b "future_field" (JsonValue/from "preserved"))
    (.build b)))

(defn- turn-page
  [^TurnService service ^TurnListParams params turns has-more]
  (let [first-id (.id ^Turn (first turns))
        last-id (.id ^Turn (last turns))
        ^TurnListPageResponse$Builder b (TurnListPageResponse/builder)
        response (do
                   (.data b ^java.util.List turns)
                   (.firstId b first-id)
                   (.hasMore b (boolean has-more))
                   (.lastId b last-id)
                   (.build b))]
    (-> (TurnListPage/builder)
        (.service service)
        (.params params)
        (.response response)
        (.build))))

(defn- client-with-turns [^TurnService turns]
  (let [sessions (proxy [SessionService] []
                   (turns [] turns))
        agents (proxy [AgentService] []
                 (sessions [] sessions))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(deftest builds-turn-request-parameters
  (let [retrieve-params (api-var '->turn-retrieve-params)
        list-params (api-var '->turn-list-params)]
    (if (and retrieve-params list-params)
      (let [^TurnRetrieveParams retrieve (retrieve-params "sess_1" "turn_1")
            ^TurnListParams list (list-params "sess_1"
                                             {:after "turn_0"
                                              :limit 25
                                              :order :desc})]
        (testing "retrieve path parameters"
          (is (= "sess_1" (.sessionId retrieve)))
          (is (= "turn_1" (impl/opt-get (.turnId retrieve)))))
        (testing "list path and query parameters"
          (is (= "sess_1" (impl/opt-get (.sessionId list))))
          (is (= "turn_0" (impl/opt-get (.after list))))
          (is (= 25 (impl/opt-get (.limit list))))
          (is (= "desc" (.asString ^TurnListParams$Order
                                   (impl/opt-get (.order list))))))
        (testing "required path parameters"
          (is (= {:openai/error :missing-key :key :session-id}
                 (error-data #(retrieve-params nil "turn_1"))))
          (is (= {:openai/error :missing-key :key :turn-id}
                 (error-data #(retrieve-params "sess_1" nil))))
          (is (= {:openai/error :missing-key :key :session-id}
                 (error-data #(list-params nil {}))))))
      (is false "Agent Session Turns parameter builders are not implemented"))))

(deftest retrieves-turn-and-normalizes-response
  (let [retrieve-turn (api-var 'retrieve-turn)]
    (if retrieve-turn
      (let [captured (atom nil)
            response (turn "turn_1" Turn$Status/IN_PROGRESS)
            service (proxy [TurnService] []
                      (retrieve [params]
                        (reset! captured params)
                        response))
            client (client-with-turns service)]
        (is (= {:id "turn_1"
                :agent-id "agent_1"
                :completed-at nil
                :created-at 123
                :error nil
                :object "agent.session.turn"
                :session-id "sess_1"
                :started-at nil
                :status :in-progress
                :subagent-id nil
                :usage nil
                :future-field "preserved"}
               (retrieve-turn client "sess_1" "turn_1")))
        (is (= "turn_1"
               (impl/opt-get (.turnId ^TurnRetrieveParams @captured)))))
      (is false "openai.beta.agents.sessions.turns/retrieve-turn is not implemented"))))

(deftest lists-all-turn-pages-and-normalizes-responses
  (let [list-turns (api-var 'list-turns)]
    (if list-turns
      (let [captured (atom [])
            service-ref (atom nil)
            service (proxy [TurnService] []
                      (list [params]
                        (swap! captured conj params)
                        (let [^TurnListParams params params
                              after (impl/opt-get (.after params))]
                          (if after
                            (turn-page @service-ref params
                                       [(turn "turn_2" Turn$Status/COMPLETED)] false)
                            (turn-page @service-ref params
                                       [(turn "turn_1" Turn$Status/IN_PROGRESS)] true)))))
            _ (reset! service-ref service)
            client (client-with-turns service)
            result (list-turns client "sess_1" {:limit 1 :order :asc})]
        (is (= ["turn_1" "turn_2"] (mapv :id result)))
        (is (= [:in-progress :completed] (mapv :status result)))
        (is (= [nil "turn_1"]
               (mapv #(impl/opt-get (.after ^TurnListParams %)) @captured)))
        (is (= [1 1]
               (mapv #(impl/opt-get (.limit ^TurnListParams %)) @captured))))
      (is false "openai.beta.agents.sessions.turns/list-turns is not implemented"))))

(deftest normalizes-turn-api-errors
  (let [retrieve-turn (api-var 'retrieve-turn)]
    (if retrieve-turn
      (let [service (proxy [TurnService] []
                      (retrieve [_]
                        (throw (OpenAIIoException. "offline"))))
            client (client-with-turns service)]
        (is (= {:openai/error :io-error}
               (error-data #(retrieve-turn client "sess_1" "turn_1")))))
      (is false "openai.beta.agents.sessions.turns/retrieve-turn is not implemented"))))
