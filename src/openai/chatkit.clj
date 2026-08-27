(ns openai.chatkit
  "Clojure wrapper for the beta ChatKit API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.chatkit.sessions SessionCreateParams)
           (com.openai.models.beta.chatkit.threads ChatKitThread ChatKitThreadItemList$Data
                                                   ChatSession ChatSessionChatKitConfigurationParam
                                                   ChatSessionChatKitConfigurationParam$AutomaticThreadTitling
                                                   ChatSessionChatKitConfigurationParam$FileUpload
                                                   ChatSessionChatKitConfigurationParam$History
                                                   ChatSessionExpiresAfterParam ChatSessionRateLimitsParam
                                                   ChatSessionWorkflowParam
                                                   ChatSessionWorkflowParam$StateVariables
                                                   ChatSessionWorkflowParam$Tracing
                                                   ThreadDeleteResponse ThreadListItemsParams
                                                   ThreadListItemsParams$Order ThreadListItemsPage
                                                   ThreadListParams ThreadListPage
                                                   ThreadListParams$Order)
           (com.openai.services.blocking.beta ChatKitService)
           (com.openai.services.blocking.beta.chatkit SessionService ThreadService)))

(set! *warn-on-reflection* true)

(defn- ->state-variables [state-variables]
  (let [b (ChatSessionWorkflowParam$StateVariables/builder)]
    (doseq [[k v] state-variables]
      (.putAdditionalProperty b (name k) (JsonValue/from v)))
    (.build b)))

(defn- ->tracing [{:keys [enabled]}]
  (let [b (ChatSessionWorkflowParam$Tracing/builder)]
    (when (some? enabled) (.enabled b (boolean enabled)))
    (.build b)))

(defn- ->workflow [{:keys [id version state-variables tracing]}]
  (when-not id (impl/missing-key! :id))
  (let [b (ChatSessionWorkflowParam/builder)]
    (.id b ^String id)
    (when version (.version b ^String version))
    (when state-variables
      (.stateVariables b ^ChatSessionWorkflowParam$StateVariables
                       (->state-variables state-variables)))
    (when tracing
      (.tracing b ^ChatSessionWorkflowParam$Tracing (->tracing tracing)))
    (.build b)))

(defn- ->expires-after [{:keys [anchor seconds]}]
  (let [b (ChatSessionExpiresAfterParam/builder)]
    (when anchor (.anchor b (JsonValue/from (name anchor))))
    (when (some? seconds) (.seconds b (long seconds)))
    (.build b)))

(defn- ->rate-limits [{:keys [max-requests-per-1-minute]}]
  (let [b (ChatSessionRateLimitsParam/builder)]
    (when (some? max-requests-per-1-minute)
      (.maxRequestsPer1Minute b (long max-requests-per-1-minute)))
    (.build b)))

(defn- ->automatic-thread-titling [{:keys [enabled]}]
  (let [b (ChatSessionChatKitConfigurationParam$AutomaticThreadTitling/builder)]
    (when (some? enabled) (.enabled b (boolean enabled)))
    (.build b)))

(defn- ->file-upload [{:keys [enabled max-file-size max-files]}]
  (let [b (ChatSessionChatKitConfigurationParam$FileUpload/builder)]
    (when (some? enabled) (.enabled b (boolean enabled)))
    (when (some? max-file-size) (.maxFileSize b (long max-file-size)))
    (when (some? max-files) (.maxFiles b (long max-files)))
    (.build b)))

(defn- ->history [{:keys [enabled recent-threads]}]
  (let [b (ChatSessionChatKitConfigurationParam$History/builder)]
    (when (some? enabled) (.enabled b (boolean enabled)))
    (when (some? recent-threads) (.recentThreads b (long recent-threads)))
    (.build b)))

(defn- ->chatkit-configuration [{:keys [automatic-thread-titling file-upload history]}]
  (let [b (ChatSessionChatKitConfigurationParam/builder)]
    (when automatic-thread-titling
      (.automaticThreadTitling b ^ChatSessionChatKitConfigurationParam$AutomaticThreadTitling
                               (->automatic-thread-titling automatic-thread-titling)))
    (when file-upload
      (.fileUpload b ^ChatSessionChatKitConfigurationParam$FileUpload
                   (->file-upload file-upload)))
    (when history
      (.history b ^ChatSessionChatKitConfigurationParam$History (->history history)))
    (.build b)))

(defn- ->create-params ^SessionCreateParams [{:keys [workflow user expires-after rate-limits chatkit-configuration]}]
  (when-not workflow (impl/missing-key! :workflow))
  (let [b (SessionCreateParams/builder)]
    (.workflow b ^ChatSessionWorkflowParam (->workflow workflow))
    (when user (.user b ^String user))
    (when expires-after
      (.expiresAfter b ^ChatSessionExpiresAfterParam (->expires-after expires-after)))
    (when rate-limits
      (.rateLimits b ^ChatSessionRateLimitsParam (->rate-limits rate-limits)))
    (when chatkit-configuration
      (.chatkitConfiguration b ^ChatSessionChatKitConfigurationParam
                            (->chatkit-configuration chatkit-configuration)))
    (.build b)))

(defn- chat-session->map [^ChatSession s]
  {:id (.id s)
   :client-secret (.clientSecret s)
   :expires-at (.expiresAt s)
   :max-requests-per-1-minute (.maxRequestsPer1Minute s)
   :status (impl/->keyword (.asString (.status s)))
   :user (.user s)
   :workflow (impl/sdk-object->clj (.workflow s))
   :rate-limits (impl/sdk-object->clj (.rateLimits s))
   :chatkit-configuration (impl/sdk-object->clj (.chatkitConfiguration s))})

(defn- thread-status->keyword [^com.openai.models.beta.chatkit.threads.ChatKitThread$Status status]
  (cond
    (.isActive status) :active
    (.isLocked status) :locked
    (.isClosed status) :closed))

(defn- chatkit-thread->map [^ChatKitThread t]
  (cond-> {:id (.id t) :created-at (.createdAt t)
           :status (thread-status->keyword (.status t)) :user (.user t)}
    (.isPresent (.title t)) (assoc :title (impl/opt-get (.title t)))))

(defn- delete-response->map [^ThreadDeleteResponse r]
  {:id (.id r) :deleted (.deleted r)})

(defn create-session [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ChatKitService chatkit (.. client (beta) (chatkit))
          ^SessionService svc (.sessions chatkit)]
      (chat-session->map (.create svc (->create-params req))))))

(defn cancel-session [^OpenAIClient client ^String session-id]
  (impl/with-api-errors
    (let [^ChatKitService chatkit (.. client (beta) (chatkit))
          ^SessionService svc (.sessions chatkit)]
      (chat-session->map (.cancel svc session-id)))))

(defn retrieve-thread [^OpenAIClient client ^String thread-id]
  (impl/with-api-errors
    (let [^ChatKitService chatkit (.. client (beta) (chatkit))
          ^ThreadService svc (.threads chatkit)]
      (chatkit-thread->map (.retrieve svc thread-id)))))

(defn list-threads
  ([^OpenAIClient client] (list-threads client {}))
  ([^OpenAIClient client {:keys [after before limit order user]}]
   (impl/with-api-errors
     (let [b (ThreadListParams/builder)
           _ (when after (.after b ^String after))
           _ (when before (.before b ^String before))
           _ (when limit (.limit b (long limit)))
           _ (when order (.order b (ThreadListParams$Order/of (name order))))
           _ (when user (.user b ^String user))
           ^ChatKitService chatkit (.. client (beta) (chatkit))
           ^ThreadService svc (.threads chatkit)]
       (mapv chatkit-thread->map (impl/all-pages (.list svc (.build b))))))))

(defn delete-thread [^OpenAIClient client ^String thread-id]
  (impl/with-api-errors
    (let [^ChatKitService chatkit (.. client (beta) (chatkit))
          ^ThreadService svc (.threads chatkit)]
      (delete-response->map (.delete svc thread-id)))))

(defn list-thread-items
  ([^OpenAIClient client ^String thread-id] (list-thread-items client thread-id {}))
  ([^OpenAIClient client ^String thread-id {:keys [after before limit order]}]
   (impl/with-api-errors
     (let [b (ThreadListItemsParams/builder)
           _ (.threadId b thread-id)
           _ (when after (.after b ^String after))
           _ (when before (.before b ^String before))
           _ (when limit (.limit b (long limit)))
           _ (when order (.order b (ThreadListItemsParams$Order/of (name order))))
           ^ChatKitService chatkit (.. client (beta) (chatkit))
           ^ThreadService svc (.threads chatkit)]
       (mapv (fn [^ChatKitThreadItemList$Data item] (impl/sdk-object->clj item))
             (impl/all-pages (.listItems svc (.build b))))))))

(defn list-threads-lazy
  "Lazy sibling of `list-threads`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client] (list-threads-lazy client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^ChatKitService chatkit (.. client (beta) (chatkit))
           ^ThreadService svc (.threads chatkit)
           b (ThreadListParams/builder)
           _ (when-let [after (:after opts)] (.after b ^String after))
           _ (when-let [before (:before opts)] (.before b ^String before))
           _ (when-let [limit (:limit opts)] (.limit b (long limit)))
           _ (when-let [order (:order opts)] (.order b (ThreadListParams$Order/of (name order))))
           _ (when-let [user (:user opts)] (.user b ^String user))
           ^ThreadListPage page (.list svc (.build b))]
       (map chatkit-thread->map (impl/lazy-pages page opts))))))

(defn list-thread-items-lazy
  "Lazy sibling of `list-thread-items`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client ^String thread-id]
   (list-thread-items-lazy client thread-id {}))
  ([^OpenAIClient client ^String thread-id opts]
   (impl/with-api-errors
     (let [^ChatKitService chatkit (.. client (beta) (chatkit))
           ^ThreadService svc (.threads chatkit)
           b (ThreadListItemsParams/builder)
           _ (.threadId b thread-id)
           _ (when-let [after (:after opts)] (.after b ^String after))
           _ (when-let [before (:before opts)] (.before b ^String before))
           _ (when-let [limit (:limit opts)] (.limit b (long limit)))
           _ (when-let [order (:order opts)] (.order b (ThreadListItemsParams$Order/of (name order))))
           ^ThreadListItemsPage page (.listItems svc (.build b))]
       (map (fn [^ChatKitThreadItemList$Data item] (impl/sdk-object->clj item))
            (impl/lazy-pages page opts))))))
