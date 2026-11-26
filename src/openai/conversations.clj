(ns openai.conversations
  "Clojure wrapper for the OpenAI Conversations API."
  (:refer-clojure :exclude [list update])
  (:require [openai.core :as core] [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.conversations Conversation ConversationCreateParams
                                              ConversationCreateParams$Metadata
                                              ConversationDeletedResource
                                              ConversationUpdateParams)
           (com.openai.models.conversations.items ConversationItem ConversationItemList
                                                    ItemCreateParams ItemDeleteParams
                                                    ItemListPage ItemListParams ItemListParams$Order
                                                    ItemRetrieveParams)
           (com.openai.models.responses ResponseIncludable)
           (com.openai.services.blocking ConversationService)
           (com.openai.services.blocking.conversations ItemService)))

(set! *warn-on-reflection* true)

(defn- ->metadata ^ConversationCreateParams$Metadata [m]
  (-> (ConversationCreateParams$Metadata/builder)
      (.additionalProperties ^java.util.Map (impl/->json-value-properties m))
      (.build)))

(defn- ->create-params ^ConversationCreateParams [{:keys [items metadata safety-identifier]}]
  (let [b (ConversationCreateParams/builder)]
    (when items (.items b ^java.util.List (mapv core/response-input-item items)))
    (when metadata (.metadata b (->metadata metadata)))
    (when safety-identifier (.safetyIdentifier b ^String safety-identifier))
    (.build b)))

(defn- conversation->map [^Conversation c]
  {:id (.id c) :created-at (.createdAt c)
   :metadata (impl/json-value->clj (._metadata c))})

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ConversationService svc (.conversations client)]
      (conversation->map (.create svc (->create-params req))))))

(defn retrieve [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^ConversationService svc (.conversations client)]
      (conversation->map (.retrieve svc id)))))

(defn- ->update-params ^ConversationUpdateParams [^String id metadata]
  (let [b (ConversationUpdateParams/builder)]
    (.conversationId b id)
    (.putAdditionalBodyProperty b "metadata" (com.openai.core.JsonValue/from metadata))
    (.build b)))

(defn update [^OpenAIClient client ^String id {:keys [metadata]}]
  (impl/with-api-errors
    (let [^ConversationService svc (.conversations client)]
      (conversation->map (.update svc (->update-params id metadata))))))

(defn delete [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^ConversationService svc (.conversations client)
          ^ConversationDeletedResource d (.delete svc id)]
      {:id (.id d) :deleted (.deleted d)})))

(defn- ->includes [include]
  (mapv #(ResponseIncludable/of (impl/enum-name %)) include))

(defn- ->item-create-params ^ItemCreateParams
  [^String conversation-id {:keys [items include]}]
  (when-not items (impl/missing-key! :items))
  (let [b (ItemCreateParams/builder)]
    (.conversationId b conversation-id)
    (.items b ^java.util.List (mapv core/response-input-item items))
    (when include (.include b ^java.util.List (->includes include)))
    (.build b)))

(defn- item->map [^ConversationItem item]
  (if-let [raw (impl/opt-get (._json item))]
    (impl/json-value->clj raw)
    {:type :unknown}))

(defn create-items [^OpenAIClient client ^String conversation-id req]
  (impl/with-api-errors
    (let [^ItemService svc (.items (.conversations client))
          ^ConversationItemList result (.create svc (->item-create-params conversation-id req))]
      (mapv item->map (.data result)))))

(defn- ->item-retrieve-params ^ItemRetrieveParams
  [^String conversation-id ^String item-id include]
  (let [b (ItemRetrieveParams/builder)]
    (.conversationId b conversation-id) (.itemId b item-id)
    (when include (.include b ^java.util.List (->includes include)))
    (.build b)))

(defn retrieve-item
  ([^OpenAIClient client ^String conversation-id ^String item-id]
   (retrieve-item client conversation-id item-id {}))
  ([^OpenAIClient client ^String conversation-id ^String item-id {:keys [include]}]
   (impl/with-api-errors
     (let [^ItemService svc (.items (.conversations client))]
       (item->map (.retrieve svc (->item-retrieve-params conversation-id item-id include)))))))

(defn- ->item-list-params ^ItemListParams
  [^String conversation-id {:keys [limit order after include]}]
  (let [b (ItemListParams/builder)]
    (.conversationId b conversation-id)
    (when limit (.limit b (long limit)))
    (when order (.order b (ItemListParams$Order/of (name order))))
    (when after (.after b ^String after))
    (when include (.include b ^java.util.List (->includes include)))
    (.build b)))

(defn list-items
  ([^OpenAIClient client ^String conversation-id] (list-items client conversation-id {}))
  ([^OpenAIClient client ^String conversation-id opts]
   (impl/with-api-errors
     (let [^ItemService svc (.items (.conversations client))
           ^ItemListPage page (.list svc (->item-list-params conversation-id opts))]
       (mapv item->map (impl/all-pages page))))))

(defn delete-item [^OpenAIClient client ^String conversation-id ^String item-id]
  (impl/with-api-errors
    (let [^ItemService svc (.items (.conversations client))
          p (-> (ItemDeleteParams/builder)
                (.conversationId conversation-id) (.itemId item-id) (.build))]
      (conversation->map (.delete svc p)))))

(def item-create create-items)
(def item-retrieve retrieve-item)
(def item-list list-items)
(def item-delete delete-item)
