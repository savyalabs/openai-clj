(ns openai.webhooks-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl]
            [openai.webhooks :as webhooks])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http Headers)
           (com.openai.models.webhooks RealtimeCallIncomingWebhookEvent SafetyDeactivationIssuedWebhookEvent SafetyWarningIssuedWebhookEvent WebhookVerificationParams)
           (com.openai.services.blocking WebhookService)
           (java.lang.reflect InvocationHandler Proxy)))

(set! *warn-on-reflection* true)

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
