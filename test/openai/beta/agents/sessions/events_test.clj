(ns openai.beta.agents.sessions.events-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http StreamResponse)
           (com.openai.models.beta.agents AgentFunctionCallOutputParam
                                           AgentSessionEvent
                                           AgentSessionEnvironmentResetEvent
                                           AgentSessionInputParam
                                           AgentSessionInputParam$AgentSessionInputToolResult
                                           AgentSessionTurnOutputTextDeltaEvent)
           (com.openai.models.beta.agents.sessions.events EventCreateParams
                                                           EventStreamParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions EventService)))

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

(defn- implementation-var [symbol]
  (try
    (requiring-resolve symbol)
    (catch java.io.FileNotFoundException _
      nil)))

(defn- client-for [^EventService event-service]
  (let [sessions (proxy [SessionService] []
                   (events [] event-service))
        agents (proxy [AgentService] []
                 (sessions [] sessions))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(deftest creates-session-events
  (let [build-params (implementation-var
                      'openai.beta.agents.sessions.events/->create-params)
        create (implementation-var 'openai.beta.agents.sessions.events/create)]
    (if (and build-params create)
      (let [request {:events [{:type :agent.session.input.tool-result
                               :call-id "call_1"
                               :success true
                               :turn-id "turn_1"
                               :output "done"}]
                     :idempotency-key "idem_1"
                     :client-context {:trace-id "trace_1"}}
            ^EventCreateParams params (build-params "sess_1" request)
            captured (atom nil)
            event-service (proxy [EventService] []
                            (create [p] (reset! captured p) nil))
            client (client-for event-service)
            ^AgentSessionInputParam event (first (.events params))
            ^AgentSessionInputParam$AgentSessionInputToolResult tool-result
            (.asAgentSessionInputToolResult event)]
        (testing "request parameters and generic event payloads"
          (is (= "sess_1" (.get (.sessionId params))))
          (is (= "idem_1" (.get (.idempotencyKey params))))
          (is (.isAgentSessionInputToolResult event))
          (is (= "call_1" (.callId tool-result)))
          (is (= "done" (.asString ^AgentFunctionCallOutputParam
                                    (.get (.output tool-result)))))
          (is (= {:trace_id "trace_1"}
                 (-> params ._additionalBodyProperties
                     (get "client_context") json-value->clj))))
        (testing "service call"
          (is (nil? (create client "sess_1" request)))
          (is (= "turn_1"
                 (.turnId ^AgentSessionInputParam$AgentSessionInputToolResult
                          (.asAgentSessionInputToolResult
                           ^AgentSessionInputParam
                           (first (.events ^EventCreateParams @captured)))))))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :session-id}
                 (error-data #(create client nil request))))
          (is (= {:openai/error :missing-key :key :events}
                 (error-data #(create client "sess_1" {}))))))
      (is false "openai.beta.agents.sessions.events/create is not implemented"))))

(deftest streams-session-events
  (let [build-params (implementation-var
                      'openai.beta.agents.sessions.events/->stream-params)
        stream (implementation-var 'openai.beta.agents.sessions.events/stream)]
    (if (and build-params stream)
      (let [event (AgentSessionEvent/ofTurnOutputTextDelta
                   (-> (AgentSessionTurnOutputTextDeltaEvent/builder)
                       (.contentIndex 0)
                       (.delta "hello")
                       (.eventId "event_1")
                       (.itemId "item_1")
                       (.outputIndex 0)
                       (.sessionId "sess_1")
                       (.turnId "turn_1")
                       (.build)))
            captured (atom nil)
            received (atom [])
            closed? (atom false)
            ^java.util.ArrayList streamed-events (java.util.ArrayList.)
            _ (.add streamed-events event)
            response (proxy [StreamResponse] []
                       (stream [] (.stream streamed-events))
                       (close [] (reset! closed? true)))
            event-service (proxy [EventService] []
                            (streamStreaming [p]
                              (reset! captured p)
                              response))
            client (client-for event-service)
            ^EventStreamParams params (build-params "sess_1")]
        (is (= "sess_1" (.get (.sessionId params))))
        (is (nil? (stream client "sess_1" #(swap! received conj %))))
        (is (= [{:content-index 0
                 :delta "hello"
                 :event-id "event_1"
                 :item-id "item_1"
                 :output-index 0
                 :session-id "sess_1"
                 :turn-id "turn_1"
                 :type :agent.session.turn.output-text.delta}]
               @received))
        (is (= "sess_1" (-> ^EventStreamParams @captured .sessionId .get)))
        (is @closed?)
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(stream client nil identity)))))
      (is false "openai.beta.agents.sessions.events/stream is not implemented"))))

(deftest streams-session-environment-reset-events
  (let [stream (implementation-var 'openai.beta.agents.sessions.events/stream)]
    (if stream
      (let [event (AgentSessionEvent/ofEnvironmentReset
                   (-> (AgentSessionEnvironmentResetEvent/builder)
                       (.environmentId "env_1")
                       (.eventId "event_2")
                       (.resetCount 3)
                       (.sessionId "sess_1")
                       (.turnId (java.util.Optional/empty))
                       (.build)))
            received (atom [])
            ^java.util.ArrayList streamed-events (java.util.ArrayList.)
            _ (.add streamed-events event)
            response (proxy [StreamResponse] []
                       (stream [] (.stream streamed-events))
                       (close [] nil))
            event-service (proxy [EventService] []
                            (streamStreaming [_] response))]
        (is (nil? (stream (client-for event-service) "sess_1"
                          #(swap! received conj %))))
        (is (= [{:environment-id "env_1"
                 :event-id "event_2"
                 :reset-count 3
                 :session-id "sess_1"
                 :turn-id nil
                 :type :agent.session.environment.reset}]
               @received)))
      (is false "openai.beta.agents.sessions.events/stream is not implemented"))))
