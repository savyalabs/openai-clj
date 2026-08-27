(ns openai.vector-stores
  "Clojure wrapper for the OpenAI Vector Stores API."
  (:refer-clojure :exclude [list update])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models ComparisonFilter ComparisonFilter$Builder ComparisonFilter$Type
                              CompoundFilter CompoundFilter$Builder CompoundFilter$Type)
           (com.openai.models.vectorstores AutoFileChunkingStrategyParam
                                            FileChunkingStrategyParam
                                            StaticFileChunkingStrategy
                                            StaticFileChunkingStrategyObjectParam
                                            VectorStore VectorStore$ExpiresAfter VectorStore$Metadata
                                            VectorStore$FileCounts VectorStoreCreateParams
                                            VectorStoreCreateParams$Builder
                                            VectorStoreCreateParams$ExpiresAfter
                                            VectorStoreCreateParams$ExpiresAfter$Builder
                                            VectorStoreCreateParams$Metadata
                                            VectorStoreCreateParams$Metadata$Builder
                                            VectorStoreDeleted VectorStoreListPage
                                            VectorStoreListParams VectorStoreListParams$Order
                                            VectorStoreSearchPage VectorStoreSearchParams
                                            VectorStoreSearchParams$Filters
                                            VectorStoreSearchParams$RankingOptions
                                            VectorStoreSearchParams$RankingOptions$Ranker
                                            VectorStoreSearchResponse VectorStoreSearchResponse$Attributes
                                            VectorStoreSearchResponse$Content
                                            VectorStoreUpdateParams VectorStoreUpdateParams$Builder)
           (com.openai.models.vectorstores.files FileContentPage FileContentParams
                                                  FileCreateParams FileListPage FileListParams
                                                  FileListParams$Filter FileListParams$Order
                                                  FileRetrieveParams FileUpdateParams VectorStoreFile
                                                  VectorStoreFile$Attributes
                                                  VectorStoreFileDeleted)
           (com.openai.models.vectorstores.filebatches FileBatchCancelParams
                                                        FileBatchCreateParams
                                                        FileBatchListFilesPage
                                                        FileBatchListFilesParams
                                                        FileBatchListFilesParams$Filter
                                                        FileBatchListFilesParams$Order
                                                        FileBatchRetrieveParams
                                                        VectorStoreFileBatch
                                                        VectorStoreFileBatch$FileCounts)
           (com.openai.services.blocking VectorStoreService)
           (com.openai.services.blocking.vectorstores FileBatchService FileService)))

(set! *warn-on-reflection* true)

(defn- ->metadata ^VectorStoreCreateParams$Metadata [m]
  (let [^VectorStoreCreateParams$Metadata$Builder b
        (VectorStoreCreateParams$Metadata/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties m))
    (.build b)))

(defn- ->expires-after ^VectorStoreCreateParams$ExpiresAfter
  [{:keys [anchor days]}]
  (when-not days (impl/missing-key! :days))
  (let [^VectorStoreCreateParams$ExpiresAfter$Builder b
        (VectorStoreCreateParams$ExpiresAfter/builder)]
    (.anchor b (JsonValue/from (impl/enum-name (or anchor :last-active-at))))
    (.days b (long days))
    (.build b)))

(defn- ->chunking-strategy ^FileChunkingStrategyParam
  [{:keys [type max-chunk-size-tokens chunk-overlap-tokens]}]
  (case type
    :auto
    (FileChunkingStrategyParam/ofAuto
     (-> (AutoFileChunkingStrategyParam/builder)
         (.type (JsonValue/from "auto"))
         (.build)))

    :static
    (FileChunkingStrategyParam/ofStatic
     (-> (StaticFileChunkingStrategyObjectParam/builder)
         (.type (JsonValue/from "static"))
         (.static_ (-> (StaticFileChunkingStrategy/builder)
                       (.maxChunkSizeTokens (long max-chunk-size-tokens))
                       (.chunkOverlapTokens (long chunk-overlap-tokens))
                       (.build)))
         (.build)))

    (throw (ex-info "Unsupported chunking strategy" {:type type}))))

(defn- ->create-params ^VectorStoreCreateParams
  [{:keys [name file-ids expires-after metadata chunking-strategy]}]
  (let [^VectorStoreCreateParams$Builder b (VectorStoreCreateParams/builder)]
    (when name (.name b ^String name))
    (when file-ids (.fileIds b ^java.util.List file-ids))
    (when expires-after (.expiresAfter b (->expires-after expires-after)))
    (when metadata (.metadata b (->metadata metadata)))
    (when chunking-strategy (.chunkingStrategy b (->chunking-strategy chunking-strategy)))
    (.build b)))

(defn- file-counts->map [^VectorStore$FileCounts c]
  {:cancelled (.cancelled c) :completed (.completed c) :failed (.failed c)
   :in-progress (.inProgress c) :total (.total c)})

(defn- expires-after->map [^VectorStore$ExpiresAfter e]
  {:anchor :last-active-at :days (.days e)})

(defn- properties->map [properties]
  (into {}
        (map (fn [[k v]] [(keyword k) (impl/json-value->clj v)]))
        properties))

(defn- metadata->map [^VectorStore$Metadata metadata]
  (properties->map (._additionalProperties metadata)))

(defn- vector-store->map [^VectorStore s]
  (cond-> {:id (.id s) :name (.name s) :created-at (.createdAt s)
           :status (impl/->keyword (.asString (.status s)))
           :usage-bytes (.usageBytes s) :file-counts (file-counts->map (.fileCounts s))}
    (.isPresent (.expiresAt s)) (assoc :expires-at (impl/opt-get (.expiresAt s)))
    (.isPresent (.expiresAfter s)) (assoc :expires-after (expires-after->map (impl/opt-get (.expiresAfter s))))
    (.isPresent (.lastActiveAt s)) (assoc :last-active-at (impl/opt-get (.lastActiveAt s)))
    (.isPresent (.metadata s)) (assoc :metadata (metadata->map (impl/opt-get (.metadata s))))))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^VectorStoreService svc (.vectorStores client)]
      (vector-store->map (.create svc (->create-params req))))))

(defn retrieve [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^VectorStoreService svc (.vectorStores client)]
      (vector-store->map (.retrieve svc id)))))

(defn- ->update-params ^VectorStoreUpdateParams
  [^String id {:keys [name expires-after metadata]}]
  (let [^VectorStoreUpdateParams$Builder b (VectorStoreUpdateParams/builder)]
    (.vectorStoreId b id)
    (when name (.name b ^String name))
    (when metadata
      (.putAdditionalBodyProperty b "metadata" (JsonValue/from metadata)))
    (when expires-after
      (.putAdditionalBodyProperty b "expires_after" (JsonValue/from expires-after)))
    (.build b)))

(defn update [^OpenAIClient client ^String id req]
  (impl/with-api-errors
    (let [^VectorStoreService svc (.vectorStores client)]
      (vector-store->map (.update svc (->update-params id req))))))

(defn- ->list-params ^VectorStoreListParams [{:keys [limit order after before]}]
  (let [b (VectorStoreListParams/builder)]
    (when limit (.limit b (long limit)))
    (when order (.order b (VectorStoreListParams$Order/of (name order))))
    (when after (.after b ^String after))
    (when before (.before b ^String before))
    (.build b)))

(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^VectorStoreService svc (.vectorStores client)
           ^VectorStoreListPage page (.list svc (->list-params opts))]
       (mapv vector-store->map (impl/all-pages page))))))

(defn delete [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^VectorStoreService svc (.vectorStores client)
          ^VectorStoreDeleted d (.delete svc id)]
      {:id (.id d) :deleted (.deleted d)})))

(defn list-lazy
  "Lazy sibling of `list`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client] (list-lazy client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^VectorStoreService svc (.vectorStores client)
           ^VectorStoreListPage page (.list svc (->list-params opts))]
       (map vector-store->map (impl/lazy-pages page opts))))))

(defn- ->comparison-filter ^ComparisonFilter [{:keys [type key value]}]
  (let [^ComparisonFilter$Builder b (ComparisonFilter/builder)]
    (when-not type (impl/missing-key! :type))
    (when-not key (impl/missing-key! :key))
    (.type b (ComparisonFilter$Type/of (name type)))
    (.key b ^String key)
    (cond
      (string? value) (.value b ^String value)
      (number? value) (.value b (double value))
      (instance? Boolean value) (.value b (boolean value))
      :else (.value b ^String (str value)))
    (.build b)))

(defn- filter->plain [{:keys [type key value filters]}]
  (if filters
    {"type" (name type) "filters" (mapv filter->plain filters)}
    {"type" (name type) "key" key "value" value}))

(defn- ->compound-filter ^CompoundFilter [{:keys [type filters]}]
  (when-not type (impl/missing-key! :type))
  (let [^CompoundFilter$Builder b (CompoundFilter/builder)]
    (.type b (CompoundFilter$Type/of (name type)))
    (doseq [f filters]
      (if (:filters f)
        (.addFilter b (JsonValue/from (filter->plain f)))
        (.addFilter b (->comparison-filter f))))
    (.build b)))

(defn- ->search-filters ^VectorStoreSearchParams$Filters [filters]
  (if (:filters filters)
    (VectorStoreSearchParams$Filters/ofCompoundFilter (->compound-filter filters))
    (VectorStoreSearchParams$Filters/ofComparisonFilter (->comparison-filter filters))))

(defn- ->search-params ^VectorStoreSearchParams
  [^String id {:keys [query filters max-num-results ranking-options rewrite-query]}]
  (when-not query (impl/missing-key! :query))
  (let [b (VectorStoreSearchParams/builder)]
    (.vectorStoreId b id)
    (if (string? query) (.query b ^String query) (.queryOfStrings b ^java.util.List query))
    (when filters (.filters b (->search-filters filters)))
    (when max-num-results (.maxNumResults b (long max-num-results)))
    (when rewrite-query (.rewriteQuery b (boolean rewrite-query)))
    (when ranking-options
      (let [{:keys [ranker score-threshold]} ranking-options
            rb (VectorStoreSearchParams$RankingOptions/builder)]
        (when ranker (.ranker rb (VectorStoreSearchParams$RankingOptions$Ranker/of (impl/enum-name ranker))))
        (when score-threshold (.scoreThreshold rb (double score-threshold)))
        (.rankingOptions b (.build rb))))
    (.build b)))

(defn- search-content->map [^VectorStoreSearchResponse$Content c]
  {:type (impl/->keyword (.asString (.type c))) :text (.text c)})

(defn- search-result->map [^VectorStoreSearchResponse r]
  (cond-> {:file-id (.fileId r) :filename (.filename r) :score (.score r)
           :content (mapv search-content->map (.content r))}
    (.isPresent (.attributes r))
    (assoc :attributes
           (properties->map
            (._additionalProperties ^VectorStoreSearchResponse$Attributes
                                    (impl/opt-get (.attributes r)))))))

(defn search [^OpenAIClient client ^String id req]
  (impl/with-api-errors
    (let [^VectorStoreService svc (.vectorStores client)
          ^VectorStoreSearchPage page (.search svc (->search-params id req))]
      (mapv search-result->map (impl/all-pages page)))))

(defn- vector-store-file->map [^VectorStoreFile f]
  (cond-> {:id (.id f) :vector-store-id (.vectorStoreId f)
           :created-at (.createdAt f) :usage-bytes (.usageBytes f)
           :status (impl/->keyword (.asString (.status f)))}
    (.isPresent (.lastError f))
    (assoc :last-error (impl/sdk-object->clj (impl/opt-get (.lastError f))))
    (.isPresent (.attributes f))
    (assoc :attributes
           (properties->map
            (._additionalProperties ^VectorStoreFile$Attributes
                                    (impl/opt-get (.attributes f)))))
    (.isPresent (.chunkingStrategy f))
    (assoc :chunking-strategy (impl/sdk-object->clj (impl/opt-get (.chunkingStrategy f))))))

(defn- ->file-create-params ^FileCreateParams
  [^String store-id {:keys [file-id attributes chunking-strategy]}]
  (when-not file-id (impl/missing-key! :file-id))
  (let [b (FileCreateParams/builder)]
    (.vectorStoreId b store-id) (.fileId b ^String file-id)
    (when attributes (.putAdditionalBodyProperty b "attributes" (JsonValue/from attributes)))
    (when chunking-strategy (.chunkingStrategy b (->chunking-strategy chunking-strategy)))
    (.build b)))

(defn create-file [^OpenAIClient client ^String store-id req]
  (impl/with-api-errors
    (let [^FileService svc (.files (.vectorStores client))]
      (vector-store-file->map (.create svc (->file-create-params store-id req))))))

(defn- ->file-retrieve-params ^FileRetrieveParams [^String store-id ^String file-id]
  (-> (FileRetrieveParams/builder) (.vectorStoreId store-id) (.fileId file-id) (.build)))

(defn retrieve-file [^OpenAIClient client ^String store-id ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files (.vectorStores client))]
      (vector-store-file->map (.retrieve svc (->file-retrieve-params store-id file-id))))))

(defn- ->file-update-params ^FileUpdateParams [^String store-id ^String file-id attributes]
  (let [b (FileUpdateParams/builder)]
    (.vectorStoreId b store-id) (.fileId b file-id)
    (.putAdditionalBodyProperty b "attributes" (JsonValue/from attributes))
    (.build b)))

(defn update-file [^OpenAIClient client ^String store-id ^String file-id {:keys [attributes]}]
  (impl/with-api-errors
    (let [^FileService svc (.files (.vectorStores client))]
      (vector-store-file->map (.update svc (->file-update-params store-id file-id attributes))))))

(defn- ->file-list-params ^FileListParams [^String store-id {:keys [filter limit order after before]}]
  (let [b (FileListParams/builder)]
    (.vectorStoreId b store-id)
    (when filter (.filter b (FileListParams$Filter/of (name filter))))
    (when limit (.limit b (long limit)))
    (when order (.order b (FileListParams$Order/of (name order))))
    (when after (.after b ^String after)) (when before (.before b ^String before))
    (.build b)))

(defn list-files
  ([^OpenAIClient client ^String store-id] (list-files client store-id {}))
  ([^OpenAIClient client ^String store-id opts]
   (impl/with-api-errors
     (let [^FileService svc (.files (.vectorStores client))
           ^FileListPage page (.list svc (->file-list-params store-id opts))]
       (mapv vector-store-file->map (impl/all-pages page))))))

(defn list-files-lazy
  "Lazy sibling of `list-files`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client ^String store-id] (list-files-lazy client store-id {}))
  ([^OpenAIClient client ^String store-id opts]
   (impl/with-api-errors
     (let [^FileService svc (.files (.vectorStores client))
           ^FileListPage page (.list svc (->file-list-params store-id opts))]
       (map vector-store-file->map (impl/lazy-pages page opts))))))

(defn delete-file [^OpenAIClient client ^String store-id ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files (.vectorStores client))
          ^VectorStoreFileDeleted d (.delete svc (-> (com.openai.models.vectorstores.files.FileDeleteParams/builder)
                                                     (.vectorStoreId store-id) (.fileId file-id) (.build)))]
      {:id (.id d) :deleted (.deleted d)})))

(defn file-content [^OpenAIClient client ^String store-id ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files (.vectorStores client))
          ^FileContentPage page (.content svc (-> (FileContentParams/builder)
                                                  (.vectorStoreId store-id) (.fileId file-id) (.build)))]
      (mapv (fn [x]
              (let [x ^com.openai.models.vectorstores.files.FileContentResponse x]
                (cond-> {}
                  (.isPresent (.type x)) (assoc :type (impl/->keyword (impl/opt-get (.type x))))
                  (.isPresent (.text x)) (assoc :text (impl/opt-get (.text x))))))
            (impl/all-pages page)))))

(defn- batch-counts->map [^VectorStoreFileBatch$FileCounts c]
  {:cancelled (.cancelled c) :completed (.completed c) :failed (.failed c)
   :in-progress (.inProgress c) :total (.total c)})

(defn- file-batch->map [^VectorStoreFileBatch b]
  {:id (.id b) :vector-store-id (.vectorStoreId b) :created-at (.createdAt b)
   :status (impl/->keyword (.asString (.status b)))
   :file-counts (batch-counts->map (.fileCounts b))})

(defn- ->batch-create-params ^FileBatchCreateParams
  [^String store-id {:keys [file-ids attributes chunking-strategy]}]
  (let [b (FileBatchCreateParams/builder)]
    (.vectorStoreId b store-id)
    (when file-ids (.fileIds b ^java.util.List file-ids))
    (when attributes (.putAdditionalBodyProperty b "attributes" (JsonValue/from attributes)))
    (when chunking-strategy (.chunkingStrategy b (->chunking-strategy chunking-strategy)))
    (.build b)))

(defn create-file-batch [^OpenAIClient client ^String store-id req]
  (impl/with-api-errors
    (let [^FileBatchService svc (.fileBatches (.vectorStores client))]
      (file-batch->map (.create svc (->batch-create-params store-id req))))))

(defn- ->batch-retrieve-params ^FileBatchRetrieveParams [^String store-id ^String batch-id]
  (-> (FileBatchRetrieveParams/builder)
      (.vectorStoreId store-id) (.batchId batch-id) (.build)))

(defn retrieve-file-batch [^OpenAIClient client ^String store-id ^String batch-id]
  (impl/with-api-errors
    (let [^FileBatchService svc (.fileBatches (.vectorStores client))]
      (file-batch->map (.retrieve svc (->batch-retrieve-params store-id batch-id))))))

(defn cancel-file-batch [^OpenAIClient client ^String store-id ^String batch-id]
  (impl/with-api-errors
    (let [^FileBatchService svc (.fileBatches (.vectorStores client))
          p (-> (FileBatchCancelParams/builder)
                (.vectorStoreId store-id) (.batchId batch-id) (.build))]
      (file-batch->map (.cancel svc p)))))

(defn- ->batch-list-params ^FileBatchListFilesParams
  [^String store-id ^String batch-id {:keys [filter limit order after before]}]
  (let [b (FileBatchListFilesParams/builder)]
    (.vectorStoreId b store-id) (.batchId b batch-id)
    (when filter (.filter b (FileBatchListFilesParams$Filter/of (name filter))))
    (when limit (.limit b (long limit)))
    (when order (.order b (FileBatchListFilesParams$Order/of (name order))))
    (when after (.after b ^String after)) (when before (.before b ^String before))
    (.build b)))

(defn file-batch-list-files
  ([^OpenAIClient client ^String store-id ^String batch-id]
   (file-batch-list-files client store-id batch-id {}))
  ([^OpenAIClient client ^String store-id ^String batch-id opts]
   (impl/with-api-errors
     (let [^FileBatchService svc (.fileBatches (.vectorStores client))
           ^FileBatchListFilesPage page
           (.listFiles svc (->batch-list-params store-id batch-id opts))]
       (mapv vector-store-file->map (impl/all-pages page))))))

(defn file-batch-list-files-lazy
  "Lazy sibling of `file-batch-list-files`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client ^String store-id ^String batch-id]
   (file-batch-list-files-lazy client store-id batch-id {}))
  ([^OpenAIClient client ^String store-id ^String batch-id opts]
   (impl/with-api-errors
     (let [^FileBatchService svc (.fileBatches (.vectorStores client))
           ^FileBatchListFilesPage page
           (.listFiles svc (->batch-list-params store-id batch-id opts))]
       (map vector-store-file->map (impl/lazy-pages page opts))))))

(def file-batch-create create-file-batch)
(def file-batch-retrieve retrieve-file-batch)
(def file-batch-cancel cancel-file-batch)
