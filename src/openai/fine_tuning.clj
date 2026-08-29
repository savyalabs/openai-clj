(ns openai.fine-tuning
  "Clojure wrapper for the OpenAI Fine-tuning API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.finetuning.jobs FineTuningJob FineTuningJob$Error
                                                FineTuningJob$Hyperparameters
                                                FineTuningJob$Hyperparameters$BatchSize
                                                FineTuningJob$Hyperparameters$LearningRateMultiplier
                                                FineTuningJob$Hyperparameters$NEpochs
                                                FineTuningJob$Metadata FineTuningJobEvent
                                                JobCreateParams
                                                JobListEventsPage JobListPage)
           (com.openai.models.finetuning.jobs.checkpoints CheckpointListPage
                                                            FineTuningJobCheckpoint
                                                            FineTuningJobCheckpoint$Metrics)
           (com.openai.models.finetuning.checkpoints.permissions PermissionCreatePage
                                                                   PermissionCreateResponse
                                                                   PermissionDeleteResponse
                                                                   PermissionListPage
                                                                   PermissionListResponse)
           (com.openai.models.finetuning.alpha.graders GraderRunParams GraderRunResponse
                                                         GraderRunResponse$Metadata
                                                         GraderRunResponse$Metadata$Errors
                                                         GraderRunResponse$Metadata$Scores
                                                         GraderRunResponse$ModelGraderTokenUsagePerModel
                                                         GraderRunResponse$SubRewards
                                                         GraderValidateParams GraderValidateResponse
                                                         GraderValidateResponse$Grader)
           (com.openai.models.graders.gradermodels StringCheckGrader
                                                    StringCheckGrader$Operation
                                                    TextSimilarityGrader
                                                    TextSimilarityGrader$EvaluationMetric)
           (com.openai.services.blocking.finetuning JobService)
           (com.openai.services.blocking.finetuning.jobs CheckpointService)
           (com.openai.services.blocking.finetuning.checkpoints PermissionService)))

(set! *warn-on-reflection* true)

(defn- json-value->clj [^JsonValue value]
  (letfn [(convert [x]
            (cond (instance? java.util.Map x)
                  (into {} (map (fn [[k v]] [(impl/->keyword k) (convert v)])) x)
                  (instance? java.util.List x) (mapv convert x)
                  :else x))]
    (convert (impl/json-value->clj value))))

(defn- json-properties->map [^java.util.Map properties]
  (into {}
        (map (fn [[k v]]
               [(impl/->keyword k) (json-value->clj ^JsonValue v)]))
        properties))

(defn- integer-or-auto [value]
  (cond
    (.isInteger ^FineTuningJob$Hyperparameters$BatchSize value) (.asInteger ^FineTuningJob$Hyperparameters$BatchSize value)
    (.isAuto ^FineTuningJob$Hyperparameters$BatchSize value) :auto))

(defn- learning-rate-or-auto [^FineTuningJob$Hyperparameters$LearningRateMultiplier value]
  (cond (.isNumber value) (.asNumber value)
        (.isAuto value) :auto))

(defn- epochs-or-auto [^FineTuningJob$Hyperparameters$NEpochs value]
  (cond (.isInteger value) (.asInteger value)
        (.isAuto value) :auto))

(defn- hyperparameters->map [^FineTuningJob$Hyperparameters hyperparameters]
  (cond-> {}
    (.isPresent (.batchSize hyperparameters))
    (assoc :batch-size (integer-or-auto (impl/opt-get (.batchSize hyperparameters))))
    (.isPresent (.learningRateMultiplier hyperparameters))
    (assoc :learning-rate-multiplier
           (learning-rate-or-auto (impl/opt-get (.learningRateMultiplier hyperparameters))))
    (.isPresent (.nEpochs hyperparameters))
    (assoc :n-epochs (epochs-or-auto (impl/opt-get (.nEpochs hyperparameters))))))

(defn- job-error->map [^FineTuningJob$Error error]
  (cond-> {:code (.code error) :message (.message error)}
    (.isPresent (.param error)) (assoc :param (impl/opt-get (.param error)))))

(defn- metadata->map [^FineTuningJob$Metadata metadata]
  (json-properties->map (._additionalProperties metadata)))

(defn- ->job-create-params ^JobCreateParams
  [{:keys [model training-file validation-file suffix seed metadata
           hyperparameters method integrations]}]
  (when-not model (impl/missing-key! :model))
  (when-not training-file (impl/missing-key! :training-file))
  (let [b (JobCreateParams/builder)]
    (.model b ^String model) (.trainingFile b ^String training-file)
    (when validation-file (.validationFile b ^String validation-file))
    (when suffix (.suffix b ^String suffix))
    (when seed (.seed b (long seed)))
    (doseq [[k v] [[:metadata metadata] [:hyperparameters hyperparameters]
                    [:method method] [:integrations integrations]] :when v]
      (.putAdditionalBodyProperty b (impl/enum-name k) (JsonValue/from v)))
    (.build b)))

(defn- job->map [^FineTuningJob j]
  (cond-> {:id (.id j) :model (.model j)
           :status (impl/->keyword (.asString (.status j)))
           :created-at (.createdAt j) :training-file (.trainingFile j)
           :hyperparameters (hyperparameters->map (.hyperparameters j))
           :result-files (vec (.resultFiles j)) :seed (.seed j)}
    (.isPresent (.finishedAt j)) (assoc :finished-at (impl/opt-get (.finishedAt j)))
    (.isPresent (.fineTunedModel j)) (assoc :fine-tuned-model (impl/opt-get (.fineTunedModel j)))
    (.isPresent (.validationFile j)) (assoc :validation-file (impl/opt-get (.validationFile j)))
    (.isPresent (.trainedTokens j)) (assoc :trained-tokens (impl/opt-get (.trainedTokens j)))
    (.isPresent (.error j)) (assoc :error (job-error->map (impl/opt-get (.error j))))
    (.isPresent (.metadata j)) (assoc :metadata (metadata->map (impl/opt-get (.metadata j))))))

(defn- event->map [^FineTuningJobEvent event]
  (cond-> {:id (.id event) :created-at (.createdAt event)
           :level (impl/->keyword (.asString (.level event)))
           :message (.message event)
           :data (json-value->clj (._data event))}
    (.isPresent (.type event))
    (assoc :type (impl/->keyword (.asString ^com.openai.models.finetuning.jobs.FineTuningJobEvent$Type
                                            (impl/opt-get (.type event)))))))

(defn- checkpoint-metrics->map [^FineTuningJobCheckpoint$Metrics metrics]
  (cond-> {}
    (.isPresent (.fullValidLoss metrics)) (assoc :full-valid-loss (impl/opt-get (.fullValidLoss metrics)))
    (.isPresent (.fullValidMeanTokenAccuracy metrics)) (assoc :full-valid-mean-token-accuracy (impl/opt-get (.fullValidMeanTokenAccuracy metrics)))
    (.isPresent (.step metrics)) (assoc :step (impl/opt-get (.step metrics)))
    (.isPresent (.trainLoss metrics)) (assoc :train-loss (impl/opt-get (.trainLoss metrics)))
    (.isPresent (.trainMeanTokenAccuracy metrics)) (assoc :train-mean-token-accuracy (impl/opt-get (.trainMeanTokenAccuracy metrics)))
    (.isPresent (.validLoss metrics)) (assoc :valid-loss (impl/opt-get (.validLoss metrics)))
    (.isPresent (.validMeanTokenAccuracy metrics)) (assoc :valid-mean-token-accuracy (impl/opt-get (.validMeanTokenAccuracy metrics)))))

(defn- checkpoint->map [^FineTuningJobCheckpoint checkpoint]
  {:id (.id checkpoint) :created-at (.createdAt checkpoint)
   :fine-tuned-model-checkpoint (.fineTunedModelCheckpoint checkpoint)
   :fine-tuning-job-id (.fineTuningJobId checkpoint)
   :metrics (checkpoint-metrics->map (.metrics checkpoint))
   :step-number (.stepNumber checkpoint)})

(defn- permission-create->map [^PermissionCreateResponse permission]
  {:id (.id permission) :created-at (.createdAt permission) :project-id (.projectId permission)})

(defn- permission-list->map [^PermissionListResponse permission]
  {:id (.id permission) :created-at (.createdAt permission) :project-id (.projectId permission)})

(defn- permission-delete->map [^PermissionDeleteResponse response]
  {:id (.id response) :deleted (.deleted response)})

(defn create-job [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^JobService svc (.jobs (.fineTuning client))]
      (job->map (.create svc (->job-create-params req))))))

(defn retrieve-job [^OpenAIClient client ^String job-id]
  (impl/with-api-errors
    (let [^JobService svc (.jobs (.fineTuning client))]
      (job->map (.retrieve svc job-id)))))

(defn- ->job-list-params ^com.openai.models.finetuning.jobs.JobListParams
  [{:keys [after limit metadata]}]
  (let [b (com.openai.models.finetuning.jobs.JobListParams/builder)]
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when metadata (.putAdditionalQueryParam b "metadata" (str metadata)))
    (.build b)))

(defn list-jobs
  ([^OpenAIClient client] (list-jobs client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^JobService svc (.jobs (.fineTuning client))
           ^JobListPage page (.list svc (->job-list-params opts))]
       (mapv job->map (impl/all-pages page))))))

(defn- job-action [^OpenAIClient client ^String job-id action]
  (impl/with-api-errors
    (let [^JobService svc (.jobs (.fineTuning client))]
      (job->map (case action :cancel (.cancel svc job-id)
                      :pause (.pause svc job-id) :resume (.resume svc job-id))))))
(defn cancel-job [client job-id] (job-action client job-id :cancel))
(defn pause-job [client job-id] (job-action client job-id :pause))
(defn resume-job [client job-id] (job-action client job-id :resume))

(defn list-events
  ([^OpenAIClient client ^String job-id] (list-events client job-id {}))
  ([^OpenAIClient client ^String job-id {:keys [after limit]}]
   (impl/with-api-errors
     (let [b (com.openai.models.finetuning.jobs.JobListEventsParams/builder)
           _ (.fineTuningJobId b job-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           ^JobService svc (.jobs (.fineTuning client))
           ^JobListEventsPage page (.listEvents svc (.build b))]
       (mapv event->map (impl/all-pages page))))))

(defn list-checkpoints
  ([^OpenAIClient client ^String job-id] (list-checkpoints client job-id {}))
  ([^OpenAIClient client ^String job-id {:keys [after limit]}]
   (impl/with-api-errors
     (let [b (com.openai.models.finetuning.jobs.checkpoints.CheckpointListParams/builder)
           _ (.fineTuningJobId b job-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           ^CheckpointService svc (.checkpoints (.jobs (.fineTuning client)))
           ^CheckpointListPage page (.list svc (.build b))]
       (mapv checkpoint->map (impl/all-pages page))))))

(defn create-permission [^OpenAIClient client ^String checkpoint-id project-ids]
  (impl/with-api-errors
    (let [b (com.openai.models.finetuning.checkpoints.permissions.PermissionCreateParams/builder)
          _ (.fineTunedModelCheckpoint b checkpoint-id)
          _ (.projectIds b ^java.util.List project-ids)
          ^PermissionService svc (.permissions (.checkpoints (.fineTuning client)))
          ^PermissionCreatePage page (.create svc (.build b))]
      (mapv permission-create->map (impl/all-pages page)))))

(defn list-permissions
  ([^OpenAIClient client ^String checkpoint-id] (list-permissions client checkpoint-id {}))
  ([^OpenAIClient client ^String checkpoint-id {:keys [after limit order]}]
   (impl/with-api-errors
     (let [b (com.openai.models.finetuning.checkpoints.permissions.PermissionListParams/builder)
           _ (.fineTunedModelCheckpoint b checkpoint-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           _ (when order (.order b (com.openai.models.finetuning.checkpoints.permissions.PermissionListParams$Order/of (name order))))
           ^PermissionService svc (.permissions (.checkpoints (.fineTuning client)))
           ^PermissionListPage page (.list svc (.build b))]
       (mapv permission-list->map (impl/all-pages page))))))

(defn delete-permission [^OpenAIClient client ^String checkpoint-id ^String permission-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.finetuning.checkpoints.permissions.PermissionDeleteParams/builder)
                (.fineTunedModelCheckpoint checkpoint-id) (.permissionId permission-id) (.build))
          ^PermissionService svc (.permissions (.checkpoints (.fineTuning client)))
          ^PermissionDeleteResponse response (.delete svc p)]
      (permission-delete->map response))))

(defn- ->grader [{:keys [type name input reference operation evaluation-metric]}]
  (case (keyword type)
    :string-check
    (-> (StringCheckGrader/builder) (.name ^String name) (.input ^String input)
        (.reference ^String reference)
        (.operation (StringCheckGrader$Operation/of (impl/enum-name operation))) (.build))
    :text-similarity
    (-> (TextSimilarityGrader/builder) (.name ^String name) (.input ^String input)
        (.reference ^String reference)
        (.evaluationMetric (TextSimilarityGrader$EvaluationMetric/of
                            (impl/enum-name evaluation-metric))) (.build))
    (throw (ex-info (str "Unsupported grader type " type)
                    {:openai/error :unsupported-grader-type :type type}))))

(defn- grader-errors->map [^GraderRunResponse$Metadata$Errors errors]
  (cond-> {:formula-parse-error (.formulaParseError errors)
           :invalid-variable-error (.invalidVariableError errors)
           :model-grader-parse-error (.modelGraderParseError errors)
           :model-grader-refusal-error (.modelGraderRefusalError errors)
           :model-grader-server-error (.modelGraderServerError errors)
           :other-error (.otherError errors)
           :python-grader-runtime-error (.pythonGraderRuntimeError errors)
           :python-grader-server-error (.pythonGraderServerError errors)
           :sample-parse-error (.sampleParseError errors)
           :truncated-observation-error (.truncatedObservationError errors)
           :unresponsive-reward-error (.unresponsiveRewardError errors)}
    (.isPresent (.modelGraderServerErrorDetails errors))
    (assoc :model-grader-server-error-details (impl/opt-get (.modelGraderServerErrorDetails errors)))
    (.isPresent (.pythonGraderRuntimeErrorDetails errors))
    (assoc :python-grader-runtime-error-details (impl/opt-get (.pythonGraderRuntimeErrorDetails errors)))
    (.isPresent (.pythonGraderServerErrorType errors))
    (assoc :python-grader-server-error-type (impl/opt-get (.pythonGraderServerErrorType errors)))))

(defn- grader-metadata->map [^GraderRunResponse$Metadata metadata]
  (cond-> {:errors (grader-errors->map (.errors metadata))
           :execution-time (.executionTime metadata) :name (.name metadata)
           :scores (json-properties->map
                    (._additionalProperties ^GraderRunResponse$Metadata$Scores (.scores metadata)))
           :type (.type metadata)}
    (.isPresent (.sampledModelName metadata))
    (assoc :sampled-model-name (impl/opt-get (.sampledModelName metadata)))
    (.isPresent (.tokenUsage metadata))
    (assoc :token-usage (impl/opt-get (.tokenUsage metadata)))))

(defn- grader-run->map [^GraderRunResponse response]
  {:metadata (grader-metadata->map (.metadata response))
   :model-grader-token-usage-per-model
   (json-properties->map
    (._additionalProperties ^GraderRunResponse$ModelGraderTokenUsagePerModel
                            (.modelGraderTokenUsagePerModel response)))
   :reward (.reward response)
   :sub-rewards (json-properties->map
                 (._additionalProperties ^GraderRunResponse$SubRewards (.subRewards response)))})

(defn- string-check-grader->map [^StringCheckGrader grader]
  {:type :string-check :name (.name grader) :input (.input grader)
   :reference (.reference grader)
   :operation (impl/->keyword (.asString (.operation grader)))})

(defn- text-similarity-grader->map [^TextSimilarityGrader grader]
  {:type :text-similarity :name (.name grader) :input (.input grader)
   :reference (.reference grader)
   :evaluation-metric (impl/->keyword (.asString (.evaluationMetric grader)))})

(defn- validated-grader->map [^GraderValidateResponse$Grader grader]
  (cond (.isStringCheck grader) (string-check-grader->map (.asStringCheck grader))
        (.isTextSimilarity grader) (text-similarity-grader->map (.asTextSimilarity grader))))

(defn- grader-validate->map [^GraderValidateResponse response]
  (cond-> {}
    (.isPresent (.grader response))
    (assoc :grader (validated-grader->map (impl/opt-get (.grader response))))))

(defn run-grader [^OpenAIClient client {:keys [grader model-sample item]}]
  (impl/with-api-errors
    (let [b (GraderRunParams/builder)
          g (->grader grader)
          _ (cond (instance? StringCheckGrader g) (.grader b ^StringCheckGrader g)
                  (instance? TextSimilarityGrader g) (.grader b ^TextSimilarityGrader g))
          _ (.modelSample b ^String model-sample) _ (.item b (JsonValue/from item))
          svc (.graders (.alpha (.fineTuning client)))]
      (grader-run->map (.run ^com.openai.services.blocking.finetuning.alpha.GraderService svc
                             (.build b))))))

(defn validate-grader [^OpenAIClient client {:keys [grader]}]
  (impl/with-api-errors
    (let [b (GraderValidateParams/builder)
          g (->grader grader)
          _ (cond (instance? StringCheckGrader g) (.grader b ^StringCheckGrader g)
                  (instance? TextSimilarityGrader g) (.grader b ^TextSimilarityGrader g))
          svc (.graders (.alpha (.fineTuning client)))]
      (grader-validate->map
       (.validate ^com.openai.services.blocking.finetuning.alpha.GraderService svc (.build b))))))
