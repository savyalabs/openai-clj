(ns openai.webhooks
  "Webhook signature verification, event unwrapping, and endpoint management."
  (:refer-clojure :exclude [list update])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.core.http Headers)
           (com.openai.models.webhooks WebhookCreateParams
                                       WebhookCreateParams$EventType
                                       WebhookDeleteParams WebhookEndpoint
                                       WebhookEndpointTestResult WebhookEndpointWithSecret
                                       WebhookListPage WebhookListParams
                                       WebhookRetrieveParams WebhookRotateSecretParams
                                       WebhookTestParams WebhookTestParams$EventType
                                       WebhookUpdateParams WebhookUpdateParams$EventType
                                       WebhookVerificationParams)
           (com.openai.models.webhooks.eventtypes EventTypeListParams)
           (com.openai.services.blocking WebhookService)
           (com.openai.services.blocking.webhooks EventTypeService)))
(set! *warn-on-reflection* true)

(defn- ->headers ^Headers [headers]
  (let [b (Headers/builder)]
    (doseq [[k v] headers]
      (if (sequential? v)
        (.put b (name k) ^java.lang.Iterable v)
        (.put b (name k) (str v))))
    (.build b)))
(defn- ->verification-params ^WebhookVerificationParams [payload headers]
  (let [b (WebhookVerificationParams/builder)]
    (if (bytes? payload) (.payload b ^bytes payload) (.payload b ^String payload))
    (.headers b (->headers headers)) (.build b)))

(defn verify-signature [^OpenAIClient client payload headers]
  (try
    (let [^WebhookService svc (.webhooks client)]
      (.verifySignature svc (->verification-params payload headers)) true)
    (catch Exception e
      (throw (ex-info (or (.getMessage e) "Invalid webhook signature")
                      {:openai/error :webhook-signature} e)))))

(defn unwrap [^OpenAIClient client payload headers]
  (try
    (let [^WebhookService svc (.webhooks client)]
      (impl/sdk-object->clj (.unwrap svc (->verification-params payload headers))))
    (catch Exception e
      (throw (ex-info (or (.getMessage e) "Invalid webhook payload")
                      {:openai/error :webhook-signature} e)))))

(defn- endpoint->map [^WebhookEndpoint endpoint]
  (cond-> {:id (.id endpoint) :created-at (.createdAt endpoint)
           :event-types (vec (.eventTypes endpoint)) :name (.name endpoint)
           :url (.url endpoint)}
    (.isPresent (.signingSecretHint endpoint))
    (assoc :signing-secret-hint (impl/opt-get (.signingSecretHint endpoint)))
    (.isPresent (.updatedAt endpoint))
    (assoc :updated-at (impl/opt-get (.updatedAt endpoint)))))

(defn- endpoint-with-secret->map [^WebhookEndpointWithSecret endpoint]
  (assoc (cond-> {:id (.id endpoint) :created-at (.createdAt endpoint)
                 :event-types (vec (.eventTypes endpoint)) :name (.name endpoint)
                 :url (.url endpoint)}
           (.isPresent (.signingSecretHint endpoint))
           (assoc :signing-secret-hint (impl/opt-get (.signingSecretHint endpoint)))
           (.isPresent (.updatedAt endpoint))
           (assoc :updated-at (impl/opt-get (.updatedAt endpoint))))
         :signing-secret (.signingSecret endpoint)))

(defn- ->create-params ^WebhookCreateParams [{:keys [name url event-types]}]
  (when-not name (impl/missing-key! :name))
  (when-not url (impl/missing-key! :url))
  (when-not event-types (impl/missing-key! :event-types))
  (let [b (WebhookCreateParams/builder)]
    (.name b ^String name)
    (.url b ^String url)
    (.eventTypes b ^java.util.List
                 (mapv #(WebhookCreateParams$EventType/of (impl/enum-name %)) event-types))
    (.build b)))

(defn- ->retrieve-params ^WebhookRetrieveParams [id]
  (when-not id (impl/missing-key! :id))
  (-> (WebhookRetrieveParams/builder) (.webhookEndpointId ^String id) (.build)))

(defn- ->update-params ^WebhookUpdateParams [id {:keys [name url event-types]}]
  (when-not id (impl/missing-key! :id))
  (let [b (WebhookUpdateParams/builder)]
    (.webhookEndpointId b ^String id)
    (when name (.name b ^String name))
    (when url (.url b ^String url))
    (when event-types
      (.eventTypes b ^java.util.List
                   (mapv #(WebhookUpdateParams$EventType/of (impl/enum-name %)) event-types)))
    (.build b)))

(defn- ->list-params ^WebhookListParams [{:keys [after limit]}]
  (let [b (WebhookListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (.build b)))

(defn- ->delete-params ^WebhookDeleteParams [id]
  (when-not id (impl/missing-key! :id))
  (-> (WebhookDeleteParams/builder) (.webhookEndpointId ^String id) (.build)))

(defn- ->rotate-secret-params ^WebhookRotateSecretParams
  [id {:keys [keep-old-secret-active-for-24-hours]}]
  (when-not id (impl/missing-key! :id))
  (let [b (WebhookRotateSecretParams/builder)]
    (.webhookEndpointId b ^String id)
    (when (some? keep-old-secret-active-for-24-hours)
      (.keepOldSecretActiveFor24Hours b (boolean keep-old-secret-active-for-24-hours)))
    (.build b)))

(defn- ->test-params ^WebhookTestParams [id {:keys [event-type]}]
  (when-not id (impl/missing-key! :id))
  (when-not event-type (impl/missing-key! :event-type))
  (let [b (WebhookTestParams/builder)]
    (.webhookEndpointId b ^String id)
    (.eventType b (WebhookTestParams$EventType/of (impl/enum-name event-type)))
    (.build b)))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^WebhookService svc (.webhooks client)]
      (endpoint-with-secret->map (.create svc (->create-params req))))))

(defn retrieve [^OpenAIClient client id]
  (impl/with-api-errors
    (let [^WebhookService svc (.webhooks client)]
      (endpoint->map (.retrieve svc (->retrieve-params id))))))

(defn update [^OpenAIClient client id req]
  (impl/with-api-errors
    (let [^WebhookService svc (.webhooks client)]
      (endpoint->map (.update svc (->update-params id req))))))

(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^WebhookService svc (.webhooks client)
           ^WebhookListPage page (.list svc (->list-params opts))]
       (mapv endpoint->map (impl/all-pages page))))))

(defn delete [^OpenAIClient client id]
  (impl/with-api-errors
    (let [^WebhookService svc (.webhooks client)]
      (.delete svc (->delete-params id))
      nil)))

(defn rotate-secret [^OpenAIClient client id opts]
  (impl/with-api-errors
    (let [^WebhookService svc (.webhooks client)]
      (endpoint-with-secret->map (.rotateSecret svc (->rotate-secret-params id opts))))))

(defn test-webhook-endpoint [^OpenAIClient client id req]
  (impl/with-api-errors
    (let [^WebhookService svc (.webhooks client)
          ^WebhookEndpointTestResult result (.test svc (->test-params id req))
          ^JsonValue success (._success result)]
      {:event-type (.eventType result) :status-code (.statusCode result)
       :success (impl/json-value->clj success)
       :webhook-endpoint-id (.webhookEndpointId result)})))

(defn list-event-types [^OpenAIClient client]
  (impl/with-api-errors
    (let [^EventTypeService svc (.eventTypes (.webhooks client))]
      (vec (.data (.list svc (-> (EventTypeListParams/builder) (.build))))))))
