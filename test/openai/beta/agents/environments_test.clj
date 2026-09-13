(ns openai.beta.agents.environments-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.errors OpenAIIoException)
           (com.openai.models.beta.agents.environments EnvironmentInfo
                                                          EnvironmentInfo$Builder
                                                          EnvironmentInfo$Status
                                                          EnvironmentInfo$Type
                                                          EnvironmentRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)))

(set! *warn-on-reflection* true)

(try
  (require 'openai.beta.agents.environments)
  (catch java.io.FileNotFoundException _))

(defn- api [sym]
  (when-let [n (find-ns 'openai.beta.agents.environments)]
    (some-> (ns-resolve n sym) deref)))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- client-for [environment-service]
  (let [agents (proxy [AgentService] []
                 (environments [] environment-service))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(defn- environment-info []
  (let [^EnvironmentInfo$Builder builder (EnvironmentInfo/builder)]
    (.id builder "env_123")
    (.files builder (java.util.Collections/emptyList))
    (.object_ builder (JsonValue/from "agent.environment"))
    (.plugins builder (java.util.Collections/emptyList))
    (.skills builder (java.util.Collections/emptyList))
    (.status builder (EnvironmentInfo$Status/of "running"))
    (.type builder (EnvironmentInfo$Type/of "openai_hosted"))
    (.putAdditionalProperty builder "future_field" (JsonValue/from "kept"))
    (.build builder)))

(deftest retrieves-environment
  (if-let [retrieve (api 'retrieve)]
    (let [captured (atom nil)
          service (proxy [EnvironmentService] []
                    (retrieve [params]
                      (reset! captured params)
                      (environment-info)))
          result (retrieve (client-for service) "env_123")]
      (testing "calls the nested environment service with a typed id parameter"
        (is (instance? EnvironmentRetrieveParams @captured))
        (is (= "env_123" (.get (.environmentId ^EnvironmentRetrieveParams @captured)))))
      (testing "normalizes response keys and enum values"
        (is (= {:id "env_123"
                :files []
                :object "agent.environment"
                :plugins []
                :skills []
                :status :running
                :type :openai-hosted
                :future-field "kept"}
               result)))
      (testing "validates required parameters"
        (is (= {:openai/error :missing-key :key :environment-id}
               (error-data #(retrieve (client-for service) nil))))))
    (is false "openai.beta.agents.environments/retrieve is not implemented")))

(deftest wraps-environment-api-errors
  (if-let [retrieve (api 'retrieve)]
    (let [service (proxy [EnvironmentService] []
                    (retrieve [_]
                      (throw (OpenAIIoException. "network failed"))))]
      (is (= :io-error
             (:openai/error
              (error-data #(retrieve (client-for service) "env_123"))))))
    (is false "openai.beta.agents.environments/retrieve is not implemented")))
