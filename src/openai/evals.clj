(ns openai.evals
  "Clojure wrapper for the OpenAI Evals API."
  (:refer-clojure :exclude [list update])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.evals EvalCreateParams EvalCreateParams$DataSourceConfig
                                      EvalCreateParams$DataSourceConfig$Custom
                                      EvalCreateParams$DataSourceConfig$Custom$ItemSchema
                                      EvalCreateParams$DataSourceConfig$Logs
                                      EvalCreateParams$DataSourceConfig$StoredCompletions
                                      EvalCreateResponse EvalCreateResponse$DataSourceConfig
                                      EvalCreateResponse$Metadata EvalCreateResponse$TestingCriterion
                                      EvalCustomDataSourceConfig EvalCustomDataSourceConfig$Schema
                                      EvalDeleteResponse EvalListPage EvalListParams EvalListResponse
                                      EvalListResponse$DataSourceConfig EvalListResponse$Metadata
                                      EvalListResponse$TestingCriterion EvalRetrieveResponse
                                      EvalRetrieveResponse$DataSourceConfig EvalRetrieveResponse$Metadata
                                      EvalRetrieveResponse$TestingCriterion EvalStoredCompletionsDataSourceConfig
                                      EvalUpdateResponse EvalUpdateResponse$DataSourceConfig
                                      EvalUpdateResponse$Metadata EvalUpdateResponse$TestingCriterion
                                      EvalListParams$Order EvalListParams$OrderBy)
           (com.openai.models.evals.runs CreateEvalCompletionsRunDataSource
                                           CreateEvalCompletionsRunDataSource$Source
                                           CreateEvalCompletionsRunDataSource$Source$FileId
                                           CreateEvalJsonlRunDataSource
                                           CreateEvalJsonlRunDataSource$Source
                                           CreateEvalJsonlRunDataSource$Source$FileId EvalApiError
                                           RunCancelResponse RunCancelResponse$DataSource
                                           RunCancelResponse$Metadata RunCancelResponse$PerModelUsage
                                           RunCancelResponse$PerTestingCriteriaResult RunCancelResponse$ResultCounts
                                           RunCreateParams RunCreateResponse RunCreateResponse$DataSource
                                           RunCreateResponse$Metadata RunCreateResponse$PerModelUsage
                                           RunCreateResponse$PerTestingCriteriaResult RunCreateResponse$ResultCounts
                                           RunDeleteResponse RunListPage RunListParams RunListResponse
                                           RunListResponse$DataSource RunListResponse$Metadata
                                           RunListResponse$PerModelUsage RunListResponse$PerTestingCriteriaResult
                                           RunListResponse$ResultCounts RunListParams$Order RunRetrieveResponse
                                           RunRetrieveResponse$DataSource RunRetrieveResponse$Metadata
                                           RunRetrieveResponse$PerModelUsage
                                           RunRetrieveResponse$PerTestingCriteriaResult
                                           RunRetrieveResponse$ResultCounts
                                           RunListParams$Status)
           (com.openai.models.evals.runs.outputitems OutputItemListPage
                                                       OutputItemListParams
                                                       OutputItemListParams$Order
                                                       OutputItemListParams$Status
                                                       OutputItemListResponse
                                                       OutputItemListResponse$DatasourceItem
                                                       OutputItemListResponse$Result
                                                       OutputItemListResponse$Result$Sample
                                                       OutputItemListResponse$Sample
                                                       OutputItemListResponse$Sample$Input
                                                       OutputItemListResponse$Sample$Output
                                                       OutputItemListResponse$Sample$Usage
                                                       OutputItemRetrieveParams OutputItemRetrieveResponse
                                                       OutputItemRetrieveResponse$DatasourceItem
                                                       OutputItemRetrieveResponse$Result
                                                       OutputItemRetrieveResponse$Result$Sample
                                                       OutputItemRetrieveResponse$Sample
                                                       OutputItemRetrieveResponse$Sample$Input
                                                       OutputItemRetrieveResponse$Sample$Output
                                                       OutputItemRetrieveResponse$Sample$Usage)
           (com.openai.models.graders.gradermodels LabelModelGrader PythonGrader
                                                    ScoreModelGrader StringCheckGrader
                                                    TextSimilarityGrader
                                                    StringCheckGrader$Operation)
           (com.openai.services.blocking EvalService)
           (com.openai.services.blocking.evals RunService)
           (com.openai.services.blocking.evals.runs OutputItemService)))
(set! *warn-on-reflection* true)

(defn- properties->map [^java.util.Map properties]
  (into {} (map (fn [[k v]] [(impl/->keyword k) (impl/json-value->clj ^JsonValue v)])) properties))

(defn- enum-value->keyword [value]
  (when value (impl/->keyword (str value))))

(defn- json-object-keyword [^JsonValue value]
  (some-> (impl/json-value->clj value) impl/->keyword))

(defn- custom-data-source->map [^EvalCustomDataSourceConfig config]
  {:type :custom :schema (properties->map
                          (._additionalProperties ^EvalCustomDataSourceConfig$Schema (.schema config)))})

(defn- stored-data-source->map [^EvalStoredCompletionsDataSourceConfig config]
  {:type :stored-completions
   :schema (properties->map (._additionalProperties (.schema config)))})

(defn- string-check->map [^StringCheckGrader grader]
  {:type :string-check :name (.name grader) :input (.input grader)
   :reference (.reference grader) :operation (enum-value->keyword (.operation grader))})

(defn- label-model->map [^LabelModelGrader grader]
  {:type :label-model :name (.name grader) :model (.model grader)
   :labels (vec (.labels grader)) :passing-labels (vec (.passingLabels grader))})

(defn- text-similarity->map [^TextSimilarityGrader grader]
  {:type :text-similarity :name (.name grader) :input (.input grader)
   :reference (.reference grader)
   :evaluation-metric (enum-value->keyword (.evaluationMetric grader))})

(defn- python->map [^PythonGrader grader]
  (cond-> {:type :python :name (.name grader) :source (.source grader)}
    (.isPresent (.imageTag grader)) (assoc :image-tag (impl/opt-get (.imageTag grader)))))

(defn- score-model->map [^ScoreModelGrader grader]
  (cond-> {:type :score-model :name (.name grader) :model (.model grader)}
    (.isPresent (.range grader)) (assoc :range (vec (impl/opt-get (.range grader))))))

(defmacro ^:private def-eval-response-converter
  [fname response-class data-source-class criterion-class metadata-class]
  (let [r (with-meta (gensym "response") {:tag response-class})
        ds (with-meta (gensym "data_source") {:tag data-source-class})
        criterion (with-meta (gensym "criterion") {:tag criterion-class})
        metadata (with-meta (gensym "metadata") {:tag metadata-class})]
    `(defn- ~fname [~r]
       (let [~ds (.dataSourceConfig ~r)]
         (cond-> {:id (.id ~r) :created-at (.createdAt ~r) :name (.name ~r)
                  :object (json-object-keyword (._object_ ~r))
                  :data-source-config
                  (cond (.isCustom ~ds) (custom-data-source->map (.asCustom ~ds))
                        (.isStoredCompletions ~ds) (stored-data-source->map (.asStoredCompletions ~ds))
                        (.isLogs ~ds) {:type :logs}
                        :else {:type :unknown})
                  :testing-criteria
                  (mapv (fn [~criterion]
                          (cond (.isStringCheckGrader ~criterion)
                                (string-check->map (.asStringCheckGrader ~criterion))
                                (.isLabelModelGrader ~criterion)
                                (label-model->map (.asLabelModelGrader ~criterion))
                                (.isEvalGraderTextSimilarity ~criterion)
                                (text-similarity->map
                                 (.toTextSimilarityGrader (.asEvalGraderTextSimilarity ~criterion)))
                                (.isEvalGraderPython ~criterion)
                                (python->map (.toPythonGrader (.asEvalGraderPython ~criterion)))
                                (.isEvalGraderScoreModel ~criterion)
                                (score-model->map
                                 (.toScoreModelGrader (.asEvalGraderScoreModel ~criterion)))
                                :else {:type :unknown}))
                        (.testingCriteria ~r))}
           (.isPresent (.metadata ~r))
           (assoc :metadata
                  (let [~metadata (impl/opt-get (.metadata ~r))]
                    (properties->map (._additionalProperties ~metadata)))))))))

(declare eval-create-response->map eval-retrieve-response->map eval-update-response->map
         eval-list-response->map)

(def-eval-response-converter eval-create-response->map EvalCreateResponse
  EvalCreateResponse$DataSourceConfig EvalCreateResponse$TestingCriterion EvalCreateResponse$Metadata)
(def-eval-response-converter eval-retrieve-response->map EvalRetrieveResponse
  EvalRetrieveResponse$DataSourceConfig EvalRetrieveResponse$TestingCriterion EvalRetrieveResponse$Metadata)
(def-eval-response-converter eval-update-response->map EvalUpdateResponse
  EvalUpdateResponse$DataSourceConfig EvalUpdateResponse$TestingCriterion EvalUpdateResponse$Metadata)
(def-eval-response-converter eval-list-response->map EvalListResponse
  EvalListResponse$DataSourceConfig EvalListResponse$TestingCriterion EvalListResponse$Metadata)

(defn- eval-delete-response->map [^EvalDeleteResponse response]
  {:deleted (.deleted response) :eval-id (.evalId response)
   :object (impl/->keyword (.object_ response))})

(defn- jsonl-source->map [^CreateEvalJsonlRunDataSource$Source source]
  (cond (.isFileId source)
        (let [^CreateEvalJsonlRunDataSource$Source$FileId file (.asFileId source)]
          {:type :file-id :id (.id file)})
        (.isFileContent source) {:type :file-content}
        :else {:type :unknown}))

(defn- completions-source->map [^CreateEvalCompletionsRunDataSource$Source source]
  (cond (.isFileId source)
        (let [^CreateEvalCompletionsRunDataSource$Source$FileId file (.asFileId source)]
          {:type :file-id :id (.id file)})
        (.isFileContent source) {:type :file-content}
        (.isStoredCompletions source) {:type :stored-completions}
        :else {:type :unknown}))

(defn- jsonl-data-source->map [^CreateEvalJsonlRunDataSource source]
  {:type :jsonl :source (jsonl-source->map (.source source))})

(defn- completions-data-source->map [^CreateEvalCompletionsRunDataSource source]
  (cond-> {:type :completions :source (completions-source->map (.source source))}
    (.isPresent (.model source)) (assoc :model (impl/opt-get (.model source)))))

(defn- api-error->map [^EvalApiError error]
  {:code (.code error) :message (.message error)})

(defmacro ^:private def-run-response-converter
  [fname response-class data-source-class metadata-class usage-class criterion-class counts-class]
  (let [r (with-meta (gensym "response") {:tag response-class})
        ds (with-meta (gensym "data_source") {:tag data-source-class})
        md (with-meta (gensym "metadata") {:tag metadata-class})
        u (with-meta (gensym "usage") {:tag usage-class})
        c (with-meta (gensym "criterion") {:tag criterion-class})
        counts (with-meta (gensym "counts") {:tag counts-class})]
    `(defn- ~fname [~r]
       (let [~ds (.dataSource ~r)
             ~counts (.resultCounts ~r)]
         (cond-> {:id (.id ~r) :created-at (.createdAt ~r) :eval-id (.evalId ~r)
                  :model (.model ~r) :name (.name ~r)
                  :object (json-object-keyword (._object_ ~r))
                  :status (impl/->keyword (.status ~r)) :report-url (.reportUrl ~r)
                  :error (api-error->map (.error ~r))
                  :data-source (cond (.isJsonl ~ds) (jsonl-data-source->map (.asJsonl ~ds))
                                     (.isCompletions ~ds) (completions-data-source->map (.asCompletions ~ds))
                                     (.isResponses ~ds) {:type :responses}
                                     :else {:type :unknown})
                  :per-model-usage
                  (mapv (fn [~u] {:cached-tokens (.cachedTokens ~u)
                                  :completion-tokens (.completionTokens ~u)
                                  :invocation-count (.invocationCount ~u)
                                  :model-name (.modelName ~u) :prompt-tokens (.promptTokens ~u)
                                  :total-tokens (.totalTokens ~u)}) (.perModelUsage ~r))
                  :per-testing-criteria-results
                  (mapv (fn [~c] {:failed (.failed ~c) :passed (.passed ~c)
                                  :testing-criteria (.testingCriteria ~c)})
                        (.perTestingCriteriaResults ~r))
                  :result-counts {:errored (.errored ~counts) :failed (.failed ~counts)
                                  :passed (.passed ~counts) :total (.total ~counts)}}
           (.isPresent (.metadata ~r))
           (assoc :metadata (let [~md (impl/opt-get (.metadata ~r))]
                              (properties->map (._additionalProperties ~md)))))))))

(declare run-create-response->map run-retrieve-response->map run-list-response->map
         run-cancel-response->map)

(def-run-response-converter run-create-response->map RunCreateResponse
  RunCreateResponse$DataSource RunCreateResponse$Metadata RunCreateResponse$PerModelUsage
  RunCreateResponse$PerTestingCriteriaResult RunCreateResponse$ResultCounts)
(def-run-response-converter run-retrieve-response->map RunRetrieveResponse
  RunRetrieveResponse$DataSource RunRetrieveResponse$Metadata RunRetrieveResponse$PerModelUsage
  RunRetrieveResponse$PerTestingCriteriaResult RunRetrieveResponse$ResultCounts)
(def-run-response-converter run-list-response->map RunListResponse
  RunListResponse$DataSource RunListResponse$Metadata RunListResponse$PerModelUsage
  RunListResponse$PerTestingCriteriaResult RunListResponse$ResultCounts)
(def-run-response-converter run-cancel-response->map RunCancelResponse
  RunCancelResponse$DataSource RunCancelResponse$Metadata RunCancelResponse$PerModelUsage
  RunCancelResponse$PerTestingCriteriaResult RunCancelResponse$ResultCounts)

(defn- run-delete-response->map [^RunDeleteResponse response]
  (cond-> {} (.isPresent (.deleted response)) (assoc :deleted (impl/opt-get (.deleted response)))
    (.isPresent (.object_ response)) (assoc :object (impl/->keyword (impl/opt-get (.object_ response))))
    (.isPresent (.runId response)) (assoc :run-id (impl/opt-get (.runId response)))))

(defmacro ^:private def-output-item-converter
  [fname response-class datasource-class result-class result-sample-class sample-class
   input-class output-class usage-class]
  (let [r (with-meta (gensym "response") {:tag response-class})
        datasource (with-meta (gensym "datasource") {:tag datasource-class})
        result (with-meta (gensym "result") {:tag result-class})
        result-sample (with-meta (gensym "result_sample") {:tag result-sample-class})
        sample (with-meta (gensym "sample") {:tag sample-class})
        input (with-meta (gensym "input") {:tag input-class})
        output (with-meta (gensym "output") {:tag output-class})
        usage (with-meta (gensym "usage") {:tag usage-class})]
    `(defn- ~fname [~r]
       (let [~datasource (.datasourceItem ~r)
             ~sample (.sample ~r)
             ~usage (.usage ~sample)]
         {:id (.id ~r) :created-at (.createdAt ~r)
          :datasource-item (properties->map (._additionalProperties ~datasource))
          :datasource-item-id (.datasourceItemId ~r) :eval-id (.evalId ~r)
          :object (json-object-keyword (._object_ ~r)) :run-id (.runId ~r)
          :status (impl/->keyword (.status ~r))
          :results
          (mapv (fn [~result]
                  (cond-> {:name (.name ~result) :passed (.passed ~result) :score (.score ~result)}
                    (.isPresent (.type ~result))
                    (assoc :type (impl/->keyword (impl/opt-get (.type ~result))))
                    (.isPresent (.sample ~result))
                    (assoc :sample
                           (let [~result-sample (impl/opt-get (.sample ~result))]
                             (properties->map (._additionalProperties ~result-sample))))))
                (.results ~r))
          :sample {:error (api-error->map (.error ~sample))
                   :finish-reason (.finishReason ~sample)
                   :input (mapv (fn [~input] (properties->map (._additionalProperties ~input)))
                                (.input ~sample))
                   :max-completion-tokens (.maxCompletionTokens ~sample) :model (.model ~sample)
                   :output (mapv (fn [~output] (properties->map (._additionalProperties ~output)))
                                 (.output ~sample))
                   :seed (.seed ~sample) :temperature (.temperature ~sample) :top-p (.topP ~sample)
                   :usage {:cached-tokens (.cachedTokens ~usage)
                           :completion-tokens (.completionTokens ~usage)
                           :prompt-tokens (.promptTokens ~usage)
                   :total-tokens (.totalTokens ~usage)}}}))))

(declare output-item-list-response->map output-item-retrieve-response->map)

(def-output-item-converter output-item-list-response->map OutputItemListResponse
  OutputItemListResponse$DatasourceItem OutputItemListResponse$Result
  OutputItemListResponse$Result$Sample OutputItemListResponse$Sample
  OutputItemListResponse$Sample$Input OutputItemListResponse$Sample$Output
  OutputItemListResponse$Sample$Usage)
(def-output-item-converter output-item-retrieve-response->map OutputItemRetrieveResponse
  OutputItemRetrieveResponse$DatasourceItem OutputItemRetrieveResponse$Result
  OutputItemRetrieveResponse$Result$Sample OutputItemRetrieveResponse$Sample
  OutputItemRetrieveResponse$Sample$Input OutputItemRetrieveResponse$Sample$Output
  OutputItemRetrieveResponse$Sample$Usage)

(defn- ->data-source-config ^EvalCreateParams$DataSourceConfig
  [{:keys [type item-schema include-sample-schema] :as config}]
  (case (keyword type)
    :custom
    (let [schema (-> (EvalCreateParams$DataSourceConfig$Custom$ItemSchema/builder)
                     (.additionalProperties ^java.util.Map (impl/->json-schema-properties item-schema))
                     (.build))
          b (EvalCreateParams$DataSourceConfig$Custom/builder)]
      (.itemSchema b schema)
      (when (some? include-sample-schema) (.includeSampleSchema b (boolean include-sample-schema)))
      (EvalCreateParams$DataSourceConfig/ofCustom (.build b)))
    :logs
    (let [b (EvalCreateParams$DataSourceConfig$Logs/builder)]
      (.type b (JsonValue/from "logs"))
      (doseq [[k v] (dissoc config :type)] (.putAdditionalProperty b (name k) (JsonValue/from v)))
      (EvalCreateParams$DataSourceConfig/ofLogs (.build b)))
    :stored-completions
    (let [b (EvalCreateParams$DataSourceConfig$StoredCompletions/builder)]
      (.type b (JsonValue/from "stored_completions"))
      (doseq [[k v] (dissoc config :type)] (.putAdditionalProperty b (name k) (JsonValue/from v)))
      (EvalCreateParams$DataSourceConfig/ofStoredCompletions (.build b)))
    (throw (ex-info (str "Unsupported eval data source " type)
                    {:openai/error :unsupported-data-source :type type}))))

(defn- ->criterion [{:keys [type name input reference operation]}]
  (case (keyword type)
    :string-check
    (-> (StringCheckGrader/builder) (.name ^String name) (.input ^String input)
        (.reference ^String reference)
        (.operation (StringCheckGrader$Operation/of (impl/enum-name operation))) (.build))
    (throw (ex-info (str "Unsupported testing criterion " type)
                    {:openai/error :unsupported-testing-criterion :type type}))))

(defn- ->create-params ^EvalCreateParams
  [{:keys [data-source-config testing-criteria name metadata]}]
  (when-not data-source-config (impl/missing-key! :data-source-config))
  (when-not testing-criteria (impl/missing-key! :testing-criteria))
  (let [b (EvalCreateParams/builder)]
    (.dataSourceConfig b (->data-source-config data-source-config))
    (doseq [criterion testing-criteria]
      (.addTestingCriterion b ^StringCheckGrader (->criterion criterion)))
    (when name (.name b ^String name))
    (when metadata (.putAdditionalBodyProperty b "metadata" (JsonValue/from metadata)))
    (.build b)))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors (let [^EvalService svc (.evals client)]
                          (eval-create-response->map (.create svc (->create-params req))))))
(defn retrieve [^OpenAIClient client ^String eval-id]
  (impl/with-api-errors (let [^EvalService svc (.evals client)]
                          (eval-retrieve-response->map (.retrieve svc eval-id)))))
(defn update [^OpenAIClient client ^String eval-id {:keys [name metadata]}]
  (impl/with-api-errors
    (let [b (com.openai.models.evals.EvalUpdateParams/builder)
          _ (.evalId b eval-id) _ (when name (.name b ^String name))
          _ (when metadata (.putAdditionalBodyProperty b "metadata" (JsonValue/from metadata)))
          ^EvalService svc (.evals client)]
      (eval-update-response->map (.update svc (.build b))))))

(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client {:keys [after limit order order-by]}]
   (impl/with-api-errors
     (let [b (EvalListParams/builder)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           _ (when order (.order b (EvalListParams$Order/of (name order))))
           _ (when order-by (.orderBy b (EvalListParams$OrderBy/of (impl/enum-name order-by))))
           ^EvalService svc (.evals client) ^EvalListPage page (.list svc (.build b))]
       (mapv eval-list-response->map (impl/all-pages page))))))
(defn delete [^OpenAIClient client ^String eval-id]
  (impl/with-api-errors
    (let [^EvalService svc (.evals client) ^EvalDeleteResponse d (.delete svc eval-id)]
      (eval-delete-response->map d))))

(defn- ->run-data-source [{:keys [type source model]}]
  (case (keyword type)
    :jsonl (let [b (CreateEvalJsonlRunDataSource/builder)]
             (.fileIdSource b ^String (:file-id source)) (.build b))
    :completions (let [b (CreateEvalCompletionsRunDataSource/builder)]
                   (.fileIdSource b ^String (:file-id source))
                   (when model (.model b ^String model)) (.build b))
    (throw (ex-info (str "Unsupported run data source " type)
                    {:openai/error :unsupported-data-source :type type}))))

(defn- ->run-create-params ^RunCreateParams [^String eval-id {:keys [data-source name metadata]}]
  (when-not data-source (impl/missing-key! :data-source))
  (let [b (RunCreateParams/builder) ds (->run-data-source data-source)]
    (.evalId b eval-id)
    (cond (instance? CreateEvalJsonlRunDataSource ds) (.dataSource b ^CreateEvalJsonlRunDataSource ds)
          (instance? CreateEvalCompletionsRunDataSource ds) (.dataSource b ^CreateEvalCompletionsRunDataSource ds))
    (when name (.name b ^String name))
    (when metadata (.putAdditionalBodyProperty b "metadata" (JsonValue/from metadata)))
    (.build b)))
(defn create-run [^OpenAIClient client ^String eval-id req]
  (impl/with-api-errors (let [^RunService svc (.runs (.evals client))]
                          (run-create-response->map (.create svc (->run-create-params eval-id req))))))

(defn retrieve-run [^OpenAIClient client ^String eval-id ^String run-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.evals.runs.RunRetrieveParams/builder)
                (.evalId eval-id) (.runId run-id) (.build))
          ^RunService svc (.runs (.evals client))]
      (run-retrieve-response->map (.retrieve svc p)))))

(defn list-runs
  ([^OpenAIClient client ^String eval-id] (list-runs client eval-id {}))
  ([^OpenAIClient client ^String eval-id {:keys [after limit order status]}]
   (impl/with-api-errors
     (let [b (RunListParams/builder) _ (.evalId b eval-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           _ (when order (.order b (RunListParams$Order/of (name order))))
           _ (when status (.status b (RunListParams$Status/of (impl/enum-name status))))
           ^RunService svc (.runs (.evals client)) ^RunListPage page (.list svc (.build b))]
       (mapv run-list-response->map (impl/all-pages page))))))
(defn cancel-run [^OpenAIClient client ^String eval-id ^String run-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.evals.runs.RunCancelParams/builder)
                (.evalId eval-id) (.runId run-id) (.build))
          ^RunService svc (.runs (.evals client))]
      (run-cancel-response->map (.cancel svc p)))))
(defn delete-run [^OpenAIClient client ^String eval-id ^String run-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.evals.runs.RunDeleteParams/builder)
                (.evalId eval-id) (.runId run-id) (.build))
          ^RunService svc (.runs (.evals client))]
      (run-delete-response->map (.delete svc p)))))

(defn- ->output-list-params ^OutputItemListParams
  [^String eval-id ^String run-id {:keys [after limit order status]}]
  (let [b (OutputItemListParams/builder)]
    (.evalId b eval-id) (.runId b run-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (OutputItemListParams$Order/of (name order))))
    (when status (.status b (OutputItemListParams$Status/of (impl/enum-name status))))
    (.build b)))
(defn list-output-items
  ([^OpenAIClient client ^String eval-id ^String run-id]
   (list-output-items client eval-id run-id {}))
  ([^OpenAIClient client ^String eval-id ^String run-id opts]
   (impl/with-api-errors
     (let [^OutputItemService svc (.outputItems (.runs (.evals client)))
           ^OutputItemListPage page (.list svc (->output-list-params eval-id run-id opts))]
       (mapv output-item-list-response->map (impl/all-pages page))))))
(defn retrieve-output-item [^OpenAIClient client ^String eval-id ^String run-id ^String item-id]
  (impl/with-api-errors
    (let [p (-> (OutputItemRetrieveParams/builder) (.evalId eval-id) (.runId run-id)
                (.outputItemId item-id) (.build))
          ^OutputItemService svc (.outputItems (.runs (.evals client)))]
      (output-item-retrieve-response->map (.retrieve svc p)))))

(def run-create create-run)
(def run-retrieve retrieve-run)
(def run-list list-runs)
(def run-cancel cancel-run)
(def run-delete delete-run)
(def output-item-list list-output-items)
(def output-item-retrieve retrieve-output-item)
