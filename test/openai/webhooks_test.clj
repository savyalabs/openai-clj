(ns openai.webhooks-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl]
            [openai.webhooks :as webhooks])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http Headers)
           (com.openai.models.webhooks AgentEnvironmentExpiredWebhookEvent AgentEnvironmentFailedWebhookEvent AgentEnvironmentReadyWebhookEvent AgentEnvironmentSuspendedWebhookEvent
                                       AgentSessionActionRequiredWebhookEvent AgentSessionCreatedWebhookEvent
                                       LiveTransportConnectedWebhookEvent
                                       RealtimeCallIncomingWebhookEvent SafetyDeactivationIssuedWebhookEvent SafetyWarningIssuedWebhookEvent
                                       UnwrapWebhookEvent WebhookCreateParams WebhookTestParams WebhookUpdateParams WebhookVerificationParams)
           (com.openai.services.blocking WebhookService)
           (java.lang.reflect InvocationHandler Proxy)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(defn- throwing-client ^OpenAIClient []
  (let [handler (reify InvocationHandler
                  (invoke [_ _ _ _]
                    (throw (IllegalArgumentException. "bad signature"))))
        service (Proxy/newProxyInstance (.getClassLoader WebhookService)
                                        (into-array Class [WebhookService]) handler)
        client-handler (reify InvocationHandler
                         (invoke [_ _ method _]
                           (if (= "webhooks" (.getName ^java.lang.reflect.Method method))
                             service
                             (throw (UnsupportedOperationException.)))))]
    (cast OpenAIClient
          (Proxy/newProxyInstance (.getClassLoader OpenAIClient)
                                  (into-array Class [OpenAIClient]) client-handler))))

(deftest translates-headers
  (let [^Headers headers (#'webhooks/->headers
                          {:webhook-id "msg_1" :webhook-signature ["v1,a" "v1,b"]})]
    (is (= ["msg_1"] (.values headers "webhook-id")))
    (is (= ["v1,a" "v1,b"] (.values headers "webhook-signature")))))

(deftest translates-verification-payloads
  (testing "String payload"
    (let [^WebhookVerificationParams p
          (#'webhooks/->verification-params "payload" {"webhook-id" "msg_1"})]
      (is (= "payload" (String. (.payload p) "UTF-8")))
      (is (= ["msg_1"] (.values (.headers p) "webhook-id")))))
  (testing "byte array payload"
    (let [^WebhookVerificationParams p
          (#'webhooks/->verification-params (.getBytes "bytes" "UTF-8") {})]
      (is (= [98 121 116 101 115] (vec (.payload p)))))))

(deftest wraps-webhook-errors
  (let [client (throwing-client)]
    (doseq [call [#(webhooks/verify-signature client "payload" {})
                  #(webhooks/unwrap client "payload" {})]]
      (let [error (try (call) nil (catch clojure.lang.ExceptionInfo e e))]
        (is (= {:openai/error :webhook-signature} (ex-data error)))
        (is (= "bad signature" (.getMessage ^Exception error)))
        (is (instance? IllegalArgumentException (.getCause ^Exception error)))))))

(deftest converts-new-safety-and-sip-webhook-events
  (let [warning (impl/sdk-input-object
                 {:id "evt_warning" :created-at 123 :object "event"
                  :type "safety.warning_issued" :data {:id "case_1"}}
                 SafetyWarningIssuedWebhookEvent)
        deactivation (impl/sdk-input-object
                      {:id "evt_deactivation" :created-at 124 :object "event"
                       :type "safety.deactivation_issued" :data {:id "case_2"}}
                      SafetyDeactivationIssuedWebhookEvent)
        incoming (impl/sdk-input-object
                  {:id "evt_call" :created-at 125 :object "event"
                   :type "realtime.call.incoming"
                   :data {:call-id "call_1" :sip-headers [] :sip-media-security "srtp"}}
                  RealtimeCallIncomingWebhookEvent)]
    (is (= "safety.warning_issued" (:type (impl/sdk-object->clj warning))))
    (is (= "case_2" (get-in (impl/sdk-object->clj deactivation) [:data :id])))
    (is (= "srtp" (get-in (impl/sdk-object->clj incoming)
                            [:data :sip-media-security])))))

(deftest translates-agent-session-webhook-event-types
  (let [create (#'webhooks/->create-params {:name "agent" :url "https://example.test"
                                            :event-types [:agent.session.created]})
        update (#'webhooks/->update-params "we_123" {:event-types [:agent.session.created]})
        test (#'webhooks/->test-params "we_123" {:event-type :agent.session.created})]
    (is (= ["agent.session.created"]
           (mapv #(.asString %) (.eventTypes ^WebhookCreateParams create))))
    (is (= ["agent.session.created"]
           (mapv #(.asString %) (opt (.eventTypes ^WebhookUpdateParams update)))))
    (is (= "agent.session.created" (.asString (.eventType ^WebhookTestParams test))))))

(deftest webhook-agent-environment-event-types
  (let [event-types [:agent.environment.ready :agent.environment.failed]
        create (#'webhooks/->create-params {:name "agent" :url "https://example.test"
                                            :event-types event-types})
        update (#'webhooks/->update-params "we_123" {:event-types event-types})
        test-ready (#'webhooks/->test-params "we_123" {:event-type :agent.environment.ready})
        test-failed (#'webhooks/->test-params "we_123" {:event-type :agent.environment.failed})]
    (is (= ["agent.environment.ready" "agent.environment.failed"]
           (mapv #(.asString %) (.eventTypes ^WebhookCreateParams create))))
    (is (= ["agent.environment.ready" "agent.environment.failed"]
           (mapv #(.asString %) (opt (.eventTypes ^WebhookUpdateParams update)))))
    (is (= "agent.environment.ready" (.asString (.eventType ^WebhookTestParams test-ready))))
    (is (= "agent.environment.failed" (.asString (.eventType ^WebhookTestParams test-failed))))))

(deftest webhook-agent-environment-expired-suspended-event-types
  (let [event-types [:agent.environment.expired :agent.environment.suspended]
        create (#'webhooks/->create-params {:name "agent" :url "https://example.test"
                                            :event-types event-types})
        update (#'webhooks/->update-params "we_123" {:event-types event-types})
        expired (#'webhooks/->test-params "we_123" {:event-type :agent.environment.expired})
        suspended (#'webhooks/->test-params "we_123" {:event-type :agent.environment.suspended})]
    (is (= ["agent.environment.expired" "agent.environment.suspended"]
           (mapv #(.asString %) (.eventTypes ^WebhookCreateParams create))))
    (is (= ["agent.environment.expired" "agent.environment.suspended"]
           (mapv #(.asString %) (opt (.eventTypes ^WebhookUpdateParams update)))))
    (is (= "agent.environment.expired"
           (.asString (.eventType ^WebhookTestParams expired))))
    (is (= "agent.environment.suspended"
           (.asString (.eventType ^WebhookTestParams suspended))))))

(deftest converts-agent-session-unwrapped-webhook-events
  (let [created (impl/sdk-input-object
                 {:id "evt_created" :created-at 1 :object "event" :type "agent.session.created"
                  :data {:id "session_1" :environment-type "browser"}}
                 AgentSessionCreatedWebhookEvent)
        action-required (impl/sdk-input-object
                         {:id "evt_action" :created-at 2 :object "event" :type "agent.session.action_required"
                          :data {:id "session_2" :required-action {:type "approve"}}}
                         AgentSessionActionRequiredWebhookEvent)]
    (is (= {:type "agent.session.created" :data {:id "session_1" :environment-type "browser"}}
           (select-keys (impl/sdk-object->clj (UnwrapWebhookEvent/ofAgentSessionCreated created))
                        [:type :data])))
    (is (= {:type "agent.session.action_required"
            :data {:id "session_2" :required-action {:type "approve"}}}
           (select-keys (impl/sdk-object->clj (UnwrapWebhookEvent/ofAgentSessionActionRequired action-required))
                        [:type :data])))))

(deftest converts-live-transport-connected-webhook-event
  (let [event (impl/sdk-input-object
               {:id "evt_transport" :created-at 3 :object "event"
                :type "live.transport.connected" :data {:session-id "sess_1" :type "webrtc"}}
               LiveTransportConnectedWebhookEvent)]
    (is (= {:type "live.transport.connected" :data {:session-id "sess_1" :type "webrtc"}}
           (select-keys (impl/sdk-object->clj (UnwrapWebhookEvent/ofLiveTransportConnected event))
                        [:type :data])))))

(deftest webhook-unwrap-agent-environment-events
  (let [ready (impl/sdk-input-object
               {:id "evt_ready" :created-at 1 :object "event" :type "agent.environment.ready"
                :data {:id "env_123"}}
               AgentEnvironmentReadyWebhookEvent)
        failed (impl/sdk-input-object
                {:id "evt_failed" :created-at 2 :object "event" :type "agent.environment.failed"
                 :data {:id "env_456"}}
                AgentEnvironmentFailedWebhookEvent)]
    (is (= "agent.environment.ready"
           (:type (impl/sdk-object->clj (UnwrapWebhookEvent/ofAgentEnvironmentReady ready)))))
    (is (= "agent.environment.failed"
           (:type (impl/sdk-object->clj (UnwrapWebhookEvent/ofAgentEnvironmentFailed failed)))))))

(deftest webhook-unwrap-agent-environment-expired-suspended-events
  (let [expired (impl/sdk-input-object
                 {:id "evt_expired" :created-at 1 :object "event"
                  :type "agent.environment.expired" :data {:id "env_123"}}
                 AgentEnvironmentExpiredWebhookEvent)
        suspended (impl/sdk-input-object
                   {:id "evt_suspended" :created-at 2 :object "event"
                    :type "agent.environment.suspended" :data {:id "env_456"}}
                   AgentEnvironmentSuspendedWebhookEvent)]
    (is (= "agent.environment.expired"
           (:type (impl/sdk-object->clj
                   (UnwrapWebhookEvent/ofAgentEnvironmentExpired expired)))))
    (is (= "agent.environment.suspended"
           (:type (impl/sdk-object->clj
                   (UnwrapWebhookEvent/ofAgentEnvironmentSuspended suspended)))))))


(defn- api [sym]
  (some-> (ns-resolve 'openai.webhooks sym) deref))

(defn- api-error []
  (-> (com.openai.errors.BadRequestException/builder)
      (.headers (-> (com.openai.core.http.Headers/builder) (.build)))
      (.build)))

(defn- endpoint []
  (-> (com.openai.models.webhooks.WebhookEndpoint/builder)
      (.id "we_123") (.createdAt 1) (.eventTypes ["response.completed"])
      (.name "primary") (.object_ (com.openai.core.JsonValue/from "webhook_endpoint"))
      (.signingSecretHint "whsec_...") (.url "https://example.test/webhooks")
      (.updatedAt 2) (.build)))

(defn- endpoint-with-secret []
  (-> (com.openai.models.webhooks.WebhookEndpointWithSecret/builder)
      (.id "we_123") (.createdAt 1) (.eventTypes ["response.completed"])
      (.name "primary") (.object_ (com.openai.core.JsonValue/from "webhook_endpoint"))
      (.signingSecret "whsec_secret") (.signingSecretHint "whsec_...")
      (.url "https://example.test/webhooks") (.updatedAt 2) (.build)))

(defn- webhook-client [service]
  (proxy [OpenAIClient] [] (webhooks [] service)))

(deftest wraps-webhook-endpoint-management
  (if-let [create (api 'create)]
    (let [calls (atom [])
          event-types (proxy [com.openai.services.blocking.webhooks.EventTypeService] []
                        (list [params]
                          (swap! calls conj [:event-types params])
                          (-> (com.openai.models.webhooks.WebhookEventTypeList/builder)
                              (.data ["response.completed"]) (.object_ (com.openai.core.JsonValue/from "list"))
                              (.build))))
          service-ref (atom nil)
          service (proxy [WebhookService] []
                    (eventTypes [] event-types)
                    (create [params] (swap! calls conj [:create params]) (endpoint-with-secret))
                    (retrieve [params] (swap! calls conj [:retrieve params]) (endpoint))
                    (update [params] (swap! calls conj [:update params]) (endpoint))
                    (delete [params] (swap! calls conj [:delete params]) nil)
                    (rotateSecret [params] (swap! calls conj [:rotate-secret params]) (endpoint-with-secret))
                    (test [params]
                      (swap! calls conj [:test params])
                      (-> (com.openai.models.webhooks.WebhookEndpointTestResult/builder)
                          (.eventType "response.completed") (.object_ (com.openai.core.JsonValue/from "webhook_test"))
                          (.statusCode 200) (.success (com.openai.core.JsonValue/from true))
                          (.webhookEndpointId "we_123") (.build)))
                    (list [params]
                      (swap! calls conj [:list params])
                      (let [after (.after ^com.openai.models.webhooks.WebhookListParams params)
                            first-page? (or (nil? after) (not (.isPresent after)))]
                        (-> (com.openai.models.webhooks.WebhookListPage/builder)
                            (.service @service-ref) (.params params)
                            (.response (-> (com.openai.models.webhooks.WebhookEndpointList/builder)
                                           (.data (if first-page? [(endpoint)] []))
                                           (.firstId "we_123") (.hasMore false) (.lastId "we_123")
                                           (.object_ (com.openai.core.JsonValue/from "list")) (.build)))
                            (.build)))))
          _ (reset! service-ref service)
          client (webhook-client service)]
      (is (= "whsec_secret" (:signing-secret (create client {:name "primary" :url "https://example.test/webhooks" :event-types [:response-completed]}))))
      (is (= "we_123" (:id ((api 'retrieve) client "we_123"))))
      (is (= "primary" (:name ((api 'update) client "we_123" {:name "primary"}))))
      (is (= [{:id "we_123" :created-at 1 :event-types ["response.completed"] :name "primary" :signing-secret-hint "whsec_..." :url "https://example.test/webhooks" :updated-at 2}]
             ((api 'list) client {:limit 10})))
      (is (nil? ((api 'delete) client "we_123")))
      (is (= "whsec_secret" (:signing-secret ((api 'rotate-secret) client "we_123" {:keep-old-secret-active-for-24-hours true}))))
      (is (= {:event-type "response.completed" :status-code 200 :success true :webhook-endpoint-id "we_123"} ((api 'test-webhook-endpoint) client "we_123" {:event-type :response-completed})))
      (is (= ["response.completed"] ((api 'list-event-types) client))))
    (is false "webhook endpoint management is not implemented")))

(deftest webhook-endpoint-management-uses-api-errors
  (if-let [create (api 'create)]
    (let [event-types (proxy [com.openai.services.blocking.webhooks.EventTypeService] []
                        (list [_] (throw (api-error))))
          service (proxy [WebhookService] []
                    (eventTypes [] event-types)
                    (create [_] (throw (api-error)))
                    (retrieve [_] (throw (api-error)))
                    (update [_] (throw (api-error)))
                    (list [_] (throw (api-error)))
                    (delete [_] (throw (api-error)))
                    (rotateSecret [_] (throw (api-error)))
                    (test [_] (throw (api-error))))
          client (webhook-client service)
          calls [#(create client {:name "primary" :url "https://example.test" :event-types [:response-completed]})
                 #((api 'list) client)
                 #((api 'retrieve) client "we_123")
                 #((api 'update) client "we_123" {:name "primary"})
                 #((api 'delete) client "we_123")
                 #((api 'rotate-secret) client "we_123" {})
                 #((api 'test-webhook-endpoint) client "we_123" {:event-type :response-completed})
                 #((api 'list-event-types) client)]]
      (doseq [call calls]
        (try
          (call)
          (is false "expected API error")
          (catch clojure.lang.ExceptionInfo e
            (is (= :api-error (:openai/error (ex-data e))))))))
    (is false "webhook endpoint management is not implemented")))
