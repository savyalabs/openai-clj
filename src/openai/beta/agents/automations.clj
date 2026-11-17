(ns openai.beta.agents.automations
  "Clojure wrapper for the beta Agents Automations API."
  (:refer-clojure :exclude [list update])
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.automations AutomationCreateParams
                                                       AutomationCreateParams$Builder
                                                       AutomationDeleteParams
                                                       AutomationListParams
                                                       AutomationListParams$Order
                                                       AutomationPauseParams
                                                       AutomationResumeParams
                                                       AutomationRetrieveParams
                                                       AutomationScheduleParam
                                                       AutomationTargetParam
                                                       AutomationUpdateParams$Metadata
                                                       AutomationUpdateParams
                                                       AutomationUpdateParams$Builder)
           (com.openai.models.beta.agents.automations.invocations InvocationCreateParams
                                                                   InvocationListParams
                                                                   InvocationListParams$Order
                                                                   InvocationRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents AutomationService)
           (com.openai.services.blocking.beta.agents.automations InvocationService)))

(set! *warn-on-reflection* true)

(defn- automation-service ^AutomationService [^OpenAIClient client]
  (.automations ^AgentService (.agents ^BetaService (.beta client))))

(defn- ->map [value]
  (walk/postwalk (fn [x]
                   (if (map? x)
                     (cond-> x
                       (string? (:type x)) (clojure.core/update :type impl/->keyword)
                       (string? (:status x)) (clojure.core/update :status impl/->keyword)
                       (string? (:source x)) (clojure.core/update :source impl/->keyword)
                       (and (map? (:source x))
                            (= #{:type} (set (keys (:source x))))
                            (keyword? (get-in x [:source :type])))
                       (assoc :source (get-in x [:source :type]))
                       (string? (:pause-reason x)) (clojure.core/update :pause-reason impl/->keyword)
                       (string? (:skip-reason x)) (clojure.core/update :skip-reason impl/->keyword))
                     x))
                 (impl/sdk-object->clj value)))

(defn- wire-name [x]
  (if (keyword? x)
    (str/replace (name x) "-" "_")
    x))

(defn- ->wire [x]
  (walk/postwalk
   (fn [v]
     (cond
       (keyword? v) (wire-name v)
       (map-entry? v) v
       (map? v) (into {} (map (fn [[k value]] [(wire-name k) value])) v)
       :else v))
   x))

(defn- json-value ^JsonValue [x]
  (JsonValue/from (->wire x)))

(defn- ->schedule ^AutomationScheduleParam [req]
  (when-not (:type req) (impl/missing-key! :type))
  (impl/sdk-input-object req AutomationScheduleParam))

(defn- ->target ^AutomationTargetParam [req]
  (impl/sdk-input-object req AutomationTargetParam))

(defn- ->update-metadata ^AutomationUpdateParams$Metadata [req]
  (impl/sdk-input-object req AutomationUpdateParams$Metadata))

(defn- ->create-params ^AutomationCreateParams [req]
  (let [^AutomationCreateParams$Builder b (AutomationCreateParams/builder)]
    (.automationScheduleParam b (->schedule req))
    (.build b)))

(defn- ->retrieve-params ^AutomationRetrieveParams [id]
  (when-not id (impl/missing-key! :automation-id))
  (-> (AutomationRetrieveParams/builder) (.automationId ^String id) (.build)))

(defn- ->update-params ^AutomationUpdateParams [id {:keys [expression metadata name run-at target timezone] :as req}]
  (when-not id (impl/missing-key! :automation-id))
  (let [^AutomationUpdateParams$Builder b (AutomationUpdateParams/builder)]
    (.automationId b ^String id)
    (when (some? expression) (.expression b ^String expression))
    (when metadata (.metadata b (->update-metadata metadata)))
    (when (some? name) (.name b ^String name))
    (when (some? run-at) (.runAt b (long run-at)))
    (when target (.target b (->target target)))
    (when (some? timezone) (.timezone b ^String timezone))
    (doseq [[k v] (dissoc req :expression :metadata :name :run-at :target :timezone)]
      (.putAdditionalBodyProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- ->delete-params ^AutomationDeleteParams [id]
  (when-not id (impl/missing-key! :automation-id))
  (-> (AutomationDeleteParams/builder) (.automationId ^String id) (.build)))

(defn- ->pause-params ^AutomationPauseParams [id]
  (when-not id (impl/missing-key! :automation-id))
  (-> (AutomationPauseParams/builder) (.automationId ^String id) (.build)))

(defn- ->resume-params ^AutomationResumeParams [id]
  (when-not id (impl/missing-key! :automation-id))
  (-> (AutomationResumeParams/builder) (.automationId ^String id) (.build)))

(defn- ->list-params ^AutomationListParams [{:keys [after limit order]}]
  (let [b (AutomationListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (AutomationListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- ->invocation-create-params ^InvocationCreateParams [id req]
  (when-not id (impl/missing-key! :automation-id))
  (let [b (InvocationCreateParams/builder)]
    (.automationId b ^String id)
    (doseq [[k v] req]
      (.putAdditionalBodyProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- ->invocation-retrieve-params ^InvocationRetrieveParams [automation-id invocation-id]
  (when-not automation-id (impl/missing-key! :automation-id))
  (when-not invocation-id (impl/missing-key! :invocation-id))
  (-> (InvocationRetrieveParams/builder)
      (.automationId ^String automation-id) (.invocationId ^String invocation-id) (.build)))

(defn- ->invocation-list-params ^InvocationListParams [automation-id {:keys [after limit order]}]
  (when-not automation-id (impl/missing-key! :automation-id))
  (let [b (InvocationListParams/builder)]
    (.automationId b ^String automation-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (InvocationListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors (->map (.create (automation-service client) (->create-params req)))))
(defn retrieve [^OpenAIClient client id]
  (impl/with-api-errors (->map (.retrieve (automation-service client) (->retrieve-params id)))))
(defn update [^OpenAIClient client id req]
  (impl/with-api-errors (->map (.update (automation-service client) (->update-params id req)))))
(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors (mapv ->map (impl/all-pages (.list (automation-service client) (->list-params opts)))))))
(defn delete [^OpenAIClient client id]
  (impl/with-api-errors (->map (.delete (automation-service client) (->delete-params id)))))
(defn pause [^OpenAIClient client id]
  (impl/with-api-errors (->map (.pause (automation-service client) (->pause-params id)))))
(defn resume [^OpenAIClient client id]
  (impl/with-api-errors (->map (.resume (automation-service client) (->resume-params id)))))

(defn- invocation-service ^InvocationService [^OpenAIClient client]
  (.invocations (automation-service client)))
(defn invocation-create [^OpenAIClient client automation-id req]
  (impl/with-api-errors (->map (.create (invocation-service client) (->invocation-create-params automation-id req)))))
(defn invocation-retrieve [^OpenAIClient client automation-id invocation-id]
  (impl/with-api-errors (->map (.retrieve (invocation-service client) (->invocation-retrieve-params automation-id invocation-id)))))
(defn invocation-list
  ([^OpenAIClient client automation-id] (invocation-list client automation-id {}))
  ([^OpenAIClient client automation-id opts]
   (impl/with-api-errors (mapv ->map (impl/all-pages (.list (invocation-service client) (->invocation-list-params automation-id opts)))))))
