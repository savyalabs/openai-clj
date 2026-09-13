(ns openai.beta.agents.environments
  "Clojure wrapper for the beta Agents Environments API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents.environments EnvironmentInfo
                                                          EnvironmentRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)))

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

(defn- environment-service ^EnvironmentService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)]
    (.environments agents)))

(defn- ->retrieve-params ^EnvironmentRetrieveParams [environment-id]
  (when-not environment-id (impl/missing-key! :environment-id))
  (-> (EnvironmentRetrieveParams/builder)
      (.environmentId ^String environment-id)
      (.build)))

(defn- environment-info->map [^EnvironmentInfo environment]
  (normalize-value (impl/sdk-object->clj environment)))

(defn retrieve
  "Retrieve a hosted agent environment by id."
  [^OpenAIClient client environment-id]
  (impl/with-api-errors
    (let [^EnvironmentService service (environment-service client)]
      (environment-info->map
       (.retrieve service (->retrieve-params environment-id))))))
