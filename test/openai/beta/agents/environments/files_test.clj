(ns openai.beta.agents.environments.files-test
  (:refer-clojure :exclude [list])
  (:require [clojure.test :refer [deftest is testing]])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.errors OpenAIIoException)
           (com.openai.models.beta.agents HostedEnvironmentFileParam)
           (com.openai.models.beta.agents.environments.files EnvironmentFile
                                                                FileCreateParams
                                                                FileListPage
                                                                FileListPageResponse
                                                                FileListPageResponse$Builder
                                                                FileListPageResponse$Object
                                                                FileListParams)
           (com.openai.models.beta.agents.environments.files FileListParams$Order)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)
           (com.openai.services.blocking.beta.agents.environments FileService)))

(set! *warn-on-reflection* true)

(try
  (require 'openai.beta.agents.environments.files)
  (catch java.io.FileNotFoundException _))

(defn- api [sym]
  (when-let [n (find-ns 'openai.beta.agents.environments.files)]
    (some-> (ns-resolve n sym) deref)))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- client-for [file-service]
  (let [environments (proxy [EnvironmentService] []
                       (files [] file-service))
        agents (proxy [AgentService] []
                 (environments [] environments))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(defn- environment-file []
  (-> (EnvironmentFile/builder)
      (.environmentId "env_123")
      (.object_ (JsonValue/from "agent.environment.file"))
      (.path "/workspace/readme.md")
      (.sizeBytes 42)
      (.putAdditionalProperty "checksum_type" (JsonValue/from "sha256"))
      (.build)))

(deftest creates-inline-environment-file
  (if-let [create (api 'create)]
    (let [captured (atom nil)
          service (proxy [FileService] []
                    (create [params]
                      (reset! captured params)
                      (environment-file)))
          request {:type :inline
                   :path "/workspace/readme.md"
                   :data "IyBSZWFkbWU="
                   :future-option {:snake-case :kept-value}}
          result (create (client-for service) "env_123" request)
          ^FileCreateParams params @captured
          ^HostedEnvironmentFileParam body (.get (.hostedEnvironmentFileParam params))]
      (testing "converts kebab-case request keys and keyword values"
        (is (= "env_123" (.get (.environmentId params))))
        (is (.isInline body))
        (is (= {:type "inline"
                :path "/workspace/readme.md"
                :data "IyBSZWFkbWU="
                :future-option {:snake-case "kept_value"}}
               (let [inline (.asInline body)]
                 (require '[openai.impl :as impl])
                 ((resolve 'openai.impl/sdk-object->clj) inline)))))
      (testing "normalizes the response"
        (is (= {:environment-id "env_123"
                :object "agent.environment.file"
                :path "/workspace/readme.md"
                :size-bytes 42
                :checksum-type "sha256"}
               result))))
    (is false "openai.beta.agents.environments.files/create is not implemented")))

(deftest creates-environment-file-by-file-id
  (if-let [create (api 'create)]
    (let [captured (atom nil)
          service (proxy [FileService] []
                    (create [params]
                      (reset! captured params)
                      (environment-file)))]
      (create (client-for service) "env_123"
              {:type :file-id :file-id "file_123" :path "/workspace/input.txt"})
      (let [body (-> ^FileCreateParams @captured .hostedEnvironmentFileParam .get)]
        (is (.isFileId ^HostedEnvironmentFileParam body))
        (is (= "file_123" (-> ^HostedEnvironmentFileParam body .asFileId .fileId)))
        (is (= "/workspace/input.txt"
               (-> ^HostedEnvironmentFileParam body .asFileId .path)))))
    (is false "openai.beta.agents.environments.files/create is not implemented")))

(deftest lists-environment-files
  (if-let [list-files (api 'list)]
    (let [captured (atom nil)
          page* (atom nil)
          service (proxy [FileService] []
                    (list [params]
                      (reset! captured params)
                      @page*))
          params (FileListParams/none)
          ^FileListPageResponse$Builder response-builder (FileListPageResponse/builder)
          _ (.data response-builder (java.util.Collections/singletonList (environment-file)))
          _ (.hasMore response-builder false)
          _ (.next response-builder (java.util.Optional/empty))
          _ (.object_ response-builder (FileListPageResponse$Object/of "list"))
          response (.build response-builder)
          page (-> (FileListPage/builder)
                   (.service service)
                   (.params params)
                   (.response response)
                   (.build))]
      (reset! page* page)
      (is (= [{:environment-id "env_123"
               :object "agent.environment.file"
               :path "/workspace/readme.md"
               :size-bytes 42
               :checksum-type "sha256"}]
             (list-files (client-for service) "env_123"
                         {:limit 20 :order :desc :page "next_1" :path "/workspace"})))
      (let [^FileListParams actual @captured]
        (is (= "env_123" (.get (.environmentId actual))))
        (is (= 20 (.get (.limit actual))))
        (is (= "desc" (.asString ^FileListParams$Order (.get (.order actual)))))
        (is (= "next_1" (.get (.page actual))))
        (is (= "/workspace" (.get (.path actual))))))
    (is false "openai.beta.agents.environments.files/list is not implemented")))

(deftest validates-file-parameters-and-wraps-api-errors
  (if-let [create (api 'create)]
    (let [service (proxy [FileService] []
                    (create [_]
                      (throw (OpenAIIoException. "network failed"))))
          client (client-for service)]
      (is (= {:openai/error :missing-key :key :environment-id}
             (error-data #(create client nil {:type :inline :path "/a" :data "YQ=="}))))
      (is (= {:openai/error :missing-key :key :type}
             (error-data #(create client "env_123" {:path "/a" :data "YQ=="}))))
      (is (= :io-error
             (:openai/error
              (error-data #(create client "env_123"
                                   {:type :inline :path "/a" :data "YQ=="}))))))
    (is false "openai.beta.agents.environments.files/create is not implemented")))
