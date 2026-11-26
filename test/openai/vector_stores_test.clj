(ns openai.vector-stores-test
  (:require [clojure.test :refer [deftest is]]
            [openai.core :as openai]
            [openai.vector-stores :as vector-stores]
            [openai.wire-level-test :as wire])
  (:import (com.sun.net.httpserver HttpExchange)
           (com.openai.models.vectorstores VectorStore
                                                FileChunkingStrategyParam
                                                StaticFileChunkingStrategy
                                                VectorStore$FileCounts
                                                VectorStore$Metadata
                                                VectorStore$Status
                                                VectorStoreCreateParams
                                                VectorStoreCreateParams$ExpiresAfter
                                                VectorStoreSearchParams
                                                VectorStoreSearchParams$Filters)
           (com.openai.models.vectorstores.files FileCreateParams)
           (com.openai.models.vectorstores.filebatches FileBatchCreateParams)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(deftest translates-create-params
  (let [^VectorStoreCreateParams p
        (#'vector-stores/->create-params
         {:name "docs" :file-ids ["file-1"]
          :expires-after {:anchor :last-active-at :days 7}
          :chunking-strategy {:type :static :max-chunk-size-tokens 800
                              :chunk-overlap-tokens 400}
          :metadata {:team "search"}})]
    (is (= "docs" (opt (.name p))))
    (is (= ["file-1"] (opt (.fileIds p))))
    (is (= 7 (.days ^VectorStoreCreateParams$ExpiresAfter
                    (opt (.expiresAfter p)))))
    (let [^FileChunkingStrategyParam strategy (opt (.chunkingStrategy p))
          ^StaticFileChunkingStrategy static (.static_ (.asStatic strategy))]
      (is (.isStatic strategy))
      (is (= 800 (.maxChunkSizeTokens static)))
      (is (= 400 (.chunkOverlapTokens static))))))

(deftest translates-search-filter
  (let [^VectorStoreSearchParams p
        (#'vector-stores/->search-params
         "vs-1" {:query "clojure" :filters {:type :eq :key "kind" :value "docs"}})]
    (is (.isComparisonFilter ^VectorStoreSearchParams$Filters (opt (.filters p))))))

(deftest translates-file-chunking-strategies
  (let [strategy {:type :static :max-chunk-size-tokens 800
                  :chunk-overlap-tokens 400}
        ^FileCreateParams file-params
        (#'vector-stores/->file-create-params
         "vs-1" {:file-id "file-1" :chunking-strategy strategy})
        ^FileBatchCreateParams batch-params
        (#'vector-stores/->batch-create-params
         "vs-1" {:file-ids ["file-1"] :chunking-strategy strategy})]
    (is (.isStatic ^FileChunkingStrategyParam (opt (.chunkingStrategy file-params))))
    (is (.isStatic ^FileChunkingStrategyParam (opt (.chunkingStrategy batch-params))))))

(deftest converts-vector-store
  (let [counts (-> (VectorStore$FileCounts/builder)
                   (.cancelled 1) (.completed 2) (.failed 3)
                   (.inProgress 4) (.total 10) (.build))
        metadata (-> (VectorStore$Metadata/builder)
                     (.putAdditionalProperty "team" (com.openai.core.JsonValue/from "search"))
                     (.build))
        store (let [b (VectorStore/builder)]
                (.id b "vs-1") (.name b "docs") (.createdAt b 100)
                (.lastActiveAt b (java.util.Optional/empty))
                (.metadata b metadata)
                (.expiresAfter b (com.openai.core.JsonField/ofNullable nil))
                (.expiresAt b (java.util.Optional/empty))
                (.status b VectorStore$Status/COMPLETED) (.usageBytes b 42)
                (.fileCounts b counts) (.build b))]
    (is (= {:id "vs-1" :name "docs" :created-at 100
            :status :completed :usage-bytes 42
            :file-counts {:cancelled 1 :completed 2 :failed 3
                          :in-progress 4 :total 10}
            :metadata {:team "search"}}
           (#'vector-stores/vector-store->map store)))))

(deftest exposes-file-batch-prefix-api
  (doseq [sym '[file-batch-create file-batch-retrieve file-batch-cancel]]
    (is (fn? (some-> (ns-resolve 'openai.vector-stores sym) deref)))))

(deftest vector-store-helper-normalizes-api-errors
  (let [fixture (wire/start-http-fixture!
                 (fn [^HttpExchange exchange]
                   (wire/read-bytes exchange)
                   (wire/respond! exchange 400 "application/json"
                                  "{\"error\":{\"message\":\"bad request\",\"type\":\"invalid_request_error\"}}")))
        client (openai/client {:api-key "test-key" :base-url (:base-url fixture)})]
    (try
      (let [error (try
                    (vector-stores/create-file-and-poll client "vs_123" {:file-id "file_123"})
                    nil
                    (catch clojure.lang.ExceptionInfo e e))]
        (is (= {:openai/error :api-error :status 400 :error-type :bad-request}
               (ex-data error))))
      (finally (.close client) (wire/stop-http-fixture! fixture)))))
