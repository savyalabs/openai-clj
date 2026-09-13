(ns openai.beta.agents.environments.templates
  "Clojure wrapper for beta agent environment templates."
  (:refer-clojure :exclude [list update])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents.environments.templates EnvironmentTemplate
                                                                    EnvironmentTemplateDeleted
                                                                    TemplateCreateParams
                                                                    TemplateCreateParams$Body
                                                                    TemplateDeleteParams
                                                                    TemplateListPage
                                                                    TemplateListParams
                                                                    TemplateListParams$Order
                                                                    TemplateRetrieveParams
                                                                    TemplateUpdateParams
                                                                    TemplateUpdateParams$Body)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)
           (com.openai.services.blocking.beta.agents.environments TemplateService)))

(set! *warn-on-reflection* true)

(defn- normalize-value [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [k (if (and (#{:type :status} k) (string? v))
                      (impl/->keyword v)
                      (normalize-value v))]))
          value)

    (vector? value) (mapv normalize-value value)
    :else value))

(defn- template-service ^TemplateService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)
        ^EnvironmentService environments (.environments agents)]
    (.templates environments)))

(defn- ->create-params ^TemplateCreateParams [opts]
  (let [builder (TemplateCreateParams/builder)
        ^TemplateCreateParams$Body body
        (impl/sdk-input-object (or opts {}) TemplateCreateParams$Body)]
    (-> builder (.body body) (.build))))

(defn- ->retrieve-params ^TemplateRetrieveParams [environment-template-id]
  (when-not environment-template-id
    (impl/missing-key! :environment-template-id))
  (-> (TemplateRetrieveParams/builder)
      (.environmentTemplateId ^String environment-template-id)
      (.build)))

(defn- ->update-params ^TemplateUpdateParams [environment-template-id opts]
  (when-not environment-template-id
    (impl/missing-key! :environment-template-id))
  (let [builder (TemplateUpdateParams/builder)
        ^TemplateUpdateParams$Body body
        (impl/sdk-input-object (or opts {}) TemplateUpdateParams$Body)]
    (-> builder
        (.environmentTemplateId ^String environment-template-id)
        (.body body)
        (.build))))

(defn- ->list-params ^TemplateListParams [{:keys [after limit order]}]
  (let [builder (TemplateListParams/builder)]
    (when after (.after builder ^String after))
    (when limit (.limit builder (long limit)))
    (when order (.order builder (TemplateListParams$Order/of (impl/enum-name order))))
    (.build builder)))

(defn- ->delete-params ^TemplateDeleteParams [environment-template-id]
  (when-not environment-template-id
    (impl/missing-key! :environment-template-id))
  (-> (TemplateDeleteParams/builder)
      (.environmentTemplateId ^String environment-template-id)
      (.build)))

(defn- environment-template->map [^EnvironmentTemplate template]
  (normalize-value (impl/sdk-object->clj template)))

(defn create
  "Create an agent environment template."
  ([^OpenAIClient client]
   (create client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^TemplateService service (template-service client)]
       (environment-template->map
        (.create service (->create-params opts)))))))

(defn retrieve
  "Retrieve an agent environment template by id."
  [^OpenAIClient client environment-template-id]
  (impl/with-api-errors
    (let [^TemplateService service (template-service client)]
      (environment-template->map
       (.retrieve service (->retrieve-params environment-template-id))))))

(defn update
  "Update an agent environment template."
  ([^OpenAIClient client environment-template-id]
   (update client environment-template-id {}))
  ([^OpenAIClient client environment-template-id opts]
   (impl/with-api-errors
     (let [^TemplateService service (template-service client)]
       (environment-template->map
        (.update service (->update-params environment-template-id opts)))))))

(defn list
  "List agent environment templates."
  ([^OpenAIClient client]
   (list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^TemplateService service (template-service client)
           ^TemplateListPage page (.list service (->list-params opts))]
       (mapv environment-template->map (impl/all-pages page))))))

(defn delete
  "Delete an agent environment template."
  [^OpenAIClient client environment-template-id]
  (impl/with-api-errors
    (let [^TemplateService service (template-service client)
          ^EnvironmentTemplateDeleted deleted
          (.delete service (->delete-params environment-template-id))]
      (normalize-value (impl/sdk-object->clj deleted)))))
