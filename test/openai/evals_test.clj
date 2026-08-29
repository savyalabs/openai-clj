(ns openai.evals-test
  (:require [clojure.test :refer [deftest is]] [openai.evals :as evals])
  (:import (com.openai.core JsonValue)
           (com.openai.models.evals EvalCreateParams EvalCreateParams$TestingCriterion
                                    EvalCreateResponse EvalCreateResponse$Metadata
                                    EvalCustomDataSourceConfig EvalCustomDataSourceConfig$Schema)
           (com.openai.models.evals.runs CreateEvalJsonlRunDataSource
                                           CreateEvalJsonlRunDataSource$Source$FileId
                                           EvalApiError RunCreateResponse RunCreateResponse$Builder
                                           RunCreateResponse$Metadata RunCreateResponse$ResultCounts)
           (com.openai.models.evals.runs.outputitems OutputItemRetrieveResponse
                                                       OutputItemRetrieveResponse$DatasourceItem
                                                       OutputItemRetrieveResponse$Result
                                                       OutputItemRetrieveResponse$Sample
                                                       OutputItemRetrieveResponse$Sample$Builder
                                                       OutputItemRetrieveResponse$Sample$Usage)))
(set! *warn-on-reflection* true)
(deftest translates-custom-eval
  (let [^EvalCreateParams p
        (#'evals/->create-params
         {:name "quality" :data-source-config {:type :custom :item-schema {:type "object"}}
          :testing-criteria [{:type :string-check :name "exact" :input "{{sample.output_text}}"
                              :reference "{{item.answer}}" :operation :eq}]})]
    (is (= "quality" (some-> (.name p) (.get))))
    (is (.isCustom (.dataSourceConfig p)))
    (is (.isStringCheck ^EvalCreateParams$TestingCriterion
                        (first (.testingCriteria p))))))

(deftest exposes-run-and-output-item-prefix-apis
  (doseq [sym '[run-create run-retrieve run-list run-cancel run-delete
                output-item-list output-item-retrieve]]
    (is (fn? (some-> (ns-resolve 'openai.evals sym) deref)))))

(deftest curates-eval-response-unions-and-metadata
  (let [schema (-> (EvalCustomDataSourceConfig$Schema/builder)
                   (.putAdditionalProperty "type" (JsonValue/from "object")) (.build))
        config (-> (EvalCustomDataSourceConfig/builder) (.schema schema)
                   (.type (JsonValue/from "custom")) (.build))
        metadata (-> (EvalCreateResponse$Metadata/builder)
                     (.putAdditionalProperty "team_name" (JsonValue/from "quality")) (.build))
        criterion (-> (com.openai.models.graders.gradermodels.StringCheckGrader/builder)
                      (.name "exact") (.input "{{sample.output_text}}")
                      (.reference "{{item.answer}}")
                      (.operation (com.openai.models.graders.gradermodels.StringCheckGrader$Operation/of "eq"))
                      (.build))
        ^EvalCreateResponse response
        (-> (EvalCreateResponse/builder) (.id "eval_1") (.createdAt 42)
            (.dataSourceConfig config) (.metadata metadata) (.name "quality")
            (.object_ (JsonValue/from "eval")) (.addTestingCriterion criterion) (.build))]
    (is (= {:id "eval_1" :created-at 42 :name "quality" :object :eval
            :data-source-config {:type :custom :schema {:type "object"}}
            :metadata {:team-name "quality"}
            :testing-criteria [{:type :string-check :name "exact"
                                :input "{{sample.output_text}}"
                                :reference "{{item.answer}}" :operation :eq}]}
           ((deref (ns-resolve 'openai.evals 'eval-create-response->map)) response)))))

(deftest curates-run-response
  (let [source (-> (CreateEvalJsonlRunDataSource$Source$FileId/builder)
                   (.id "file_1") (.build))
        ds (-> (CreateEvalJsonlRunDataSource/builder) (.source source) (.build))
        counts (-> (RunCreateResponse$ResultCounts/builder) (.errored 1) (.failed 2)
                   (.passed 3) (.total 6) (.build))
        error (-> (EvalApiError/builder) (.code "none") (.message "") (.build))
        metadata (-> (RunCreateResponse$Metadata/builder) (.build))
        ^RunCreateResponse$Builder builder (RunCreateResponse/builder)
        _ (.id builder "run_1") _ (.createdAt builder 43) _ (.dataSource builder ds)
        _ (.error builder error) _ (.evalId builder "eval_1")
        _ (.metadata builder metadata) _ (.model builder "gpt-test") _ (.name builder "trial")
        _ (.object_ builder (JsonValue/from "eval.run"))
        _ (.perModelUsage builder (java.util.ArrayList.))
        _ (.perTestingCriteriaResults builder (java.util.ArrayList.))
        _ (.reportUrl builder "https://example.test/report")
        _ (.resultCounts builder counts) _ (.status builder "completed")
        ^RunCreateResponse response
        (.build builder)]
    (is (= {:id "run_1" :created-at 43 :eval-id "eval_1" :model "gpt-test"
            :name "trial" :object :eval.run :status :completed
            :report-url "https://example.test/report"
            :error {:code "none" :message ""} :metadata {}
            :data-source {:type :jsonl :source {:type :file-id :id "file_1"}}
            :per-model-usage [] :per-testing-criteria-results []
            :result-counts {:errored 1 :failed 2 :passed 3 :total 6}}
           ((deref (ns-resolve 'openai.evals 'run-create-response->map)) response)))))

(deftest curates-output-item-response
  (let [item (-> (OutputItemRetrieveResponse$DatasourceItem/builder)
                 (.putAdditionalProperty "prompt_text" (JsonValue/from "Hello")) (.build))
        result (-> (OutputItemRetrieveResponse$Result/builder) (.name "exact")
                   (.passed true) (.score 1.0) (.type "string_check") (.build))
        usage (-> (OutputItemRetrieveResponse$Sample$Usage/builder) (.cachedTokens 0)
                  (.completionTokens 1) (.promptTokens 2) (.totalTokens 3) (.build))
        error (-> (EvalApiError/builder) (.code "none") (.message "") (.build))
        ^OutputItemRetrieveResponse$Sample$Builder sample-builder
        (OutputItemRetrieveResponse$Sample/builder)
        _ (.error sample-builder error) _ (.finishReason sample-builder "stop")
        _ (.input sample-builder (java.util.ArrayList.))
        _ (.maxCompletionTokens sample-builder 10) _ (.model sample-builder "gpt-test")
        _ (.output sample-builder (java.util.ArrayList.)) _ (.seed sample-builder 1)
        _ (.temperature sample-builder 0.0) _ (.topP sample-builder 1.0)
        _ (.usage sample-builder usage)
        sample (.build sample-builder)
        ^OutputItemRetrieveResponse response
        (-> (OutputItemRetrieveResponse/builder) (.id "out_1") (.createdAt 44)
            (.datasourceItem item) (.datasourceItemId 7) (.evalId "eval_1")
            (.object_ (JsonValue/from "eval.run.output_item")) (.addResult result)
            (.runId "run_1") (.sample sample) (.status "pass") (.build))]
    (is (= {:id "out_1" :created-at 44 :datasource-item {:prompt-text "Hello"}
            :datasource-item-id 7 :eval-id "eval_1" :object :eval.run.output-item
            :results [{:name "exact" :passed true :score 1.0 :type :string-check}]
            :run-id "run_1" :status :pass
            :sample {:error {:code "none" :message ""} :finish-reason "stop"
                     :input [] :max-completion-tokens 10 :model "gpt-test" :output []
                     :seed 1 :temperature 0.0 :top-p 1.0
                     :usage {:cached-tokens 0 :completion-tokens 1
                             :prompt-tokens 2 :total-tokens 3}}}
           ((deref (ns-resolve 'openai.evals 'output-item-retrieve-response->map)) response)))))
