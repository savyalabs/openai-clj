(ns openai.beta.agents.environments.files
  "Clojure wrapper for files in beta agent environments."
  (:refer-clojure :exclude [list])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents HostedEnvironmentFileParam)
           (com.openai.models.beta.agents.environments.files EnvironmentFile
                                                                FileCreateParams
                                                                FileListPage
                                                                FileListParams
                                                                FileListParams$Order)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)
           (com.openai.services.blocking.beta.agents.environments FileService)))

(set! *warn-on-reflection* true)

(defn- file-service ^FileService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)
        ^EnvironmentService environments (.environments agents)]
    (.files environments)))

(defn- ->hosted-file-param ^HostedEnvironmentFileParam [{:keys [type path data file-id] :as file}]
  (when-not type (impl/missing-key! :type))
  (case (keyword type)
    :inline
    (do
      (when-not path (impl/missing-key! :path))
      (when-not data (impl/missing-key! :data)))

    :file-id
    (do
      (when-not file-id (impl/missing-key! :file-id))
      (when-not path (impl/missing-key! :path)))

    (throw (ex-info (str "Unsupported hosted environment file type " type)
                    {:openai/error :unsupported-file-type :type type})))
  (impl/sdk-input-object file HostedEnvironmentFileParam))

(defn- ->create-params ^FileCreateParams [environment-id file]
  (when-not environment-id (impl/missing-key! :environment-id))
  (let [builder (FileCreateParams/builder)]
    (.environmentId builder ^String environment-id)
    (when file
      (.hostedEnvironmentFileParam builder (->hosted-file-param file)))
    (.build builder)))

(defn- ->list-params ^FileListParams
  [environment-id {:keys [limit order page path]}]
  (when-not environment-id (impl/missing-key! :environment-id))
  (let [builder (FileListParams/builder)]
    (.environmentId builder ^String environment-id)
    (when limit (.limit builder (long limit)))
    (when order (.order builder (FileListParams$Order/of (impl/enum-name order))))
    (when page (.page builder ^String page))
    (when path (.path builder ^String path))
    (.build builder)))

(defn- environment-file->map [^EnvironmentFile file]
  (impl/sdk-object->clj file))

(defn create
  "Create a file in an agent environment from inline data or an OpenAI file id."
  ([^OpenAIClient client environment-id]
   (create client environment-id nil))
  ([^OpenAIClient client environment-id file]
   (impl/with-api-errors
     (let [^FileService service (file-service client)]
       (environment-file->map
        (.create service (->create-params environment-id file)))))))

(defn list
  "List files in an agent environment."
  ([^OpenAIClient client environment-id]
   (list client environment-id {}))
  ([^OpenAIClient client environment-id opts]
   (impl/with-api-errors
     (let [^FileService service (file-service client)
           ^FileListPage page (.list service (->list-params environment-id opts))]
       (mapv environment-file->map (impl/all-pages page))))))
