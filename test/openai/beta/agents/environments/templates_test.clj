(ns openai.beta.agents.environments.templates-test
  (:refer-clojure :exclude [list update])
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.errors OpenAIIoException)
           (com.openai.models.beta.agents.environments.templates EnvironmentTemplate
                                                                    EnvironmentTemplate$Builder
                                                                    EnvironmentTemplate$Network
                                                                    EnvironmentTemplate$Network$Access
                                                                    EnvironmentTemplate$Packages
                                                                    EnvironmentTemplate$Packages$Builder
                                                                    EnvironmentTemplateDeleted
                                                                    TemplateCreateParams
                                                                    TemplateDeleteParams
                                                                    TemplateListPage
                                                                    TemplateListPageResponse
                                                                    TemplateListPageResponse$Builder
                                                                    TemplateListParams
                                                                    TemplateListParams$Order
                                                                    TemplateRetrieveParams
                                                                    TemplateUpdateParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)
           (com.openai.services.blocking.beta.agents.environments TemplateService)))

(set! *warn-on-reflection* true)

(try
  (require 'openai.beta.agents.environments.templates)
  (catch java.io.FileNotFoundException _))

(defn- api [sym]
  (when-let [n (find-ns 'openai.beta.agents.environments.templates)]
    (some-> (ns-resolve n sym) deref)))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- client-for [template-service]
  (let [environments (proxy [EnvironmentService] []
                       (templates [] template-service))
        agents (proxy [AgentService] []
                 (environments [] environments))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(defn- template []
  (let [network (-> (EnvironmentTemplate$Network/builder)
                    (.access (EnvironmentTemplate$Network$Access/of "restricted"))
                    (.allowedDomains ["example.com"])
                    (.build))
        ^EnvironmentTemplate$Packages$Builder packages-builder
        (EnvironmentTemplate$Packages/builder)
        _ (.npm packages-builder (java.util.Collections/emptyList))
        _ (.python packages-builder (java.util.Collections/singletonList "numpy"))
        _ (.system packages-builder (java.util.Collections/emptyList))
        packages (.build packages-builder)
        ^EnvironmentTemplate$Builder builder (EnvironmentTemplate/builder)]
    (.id builder "tmpl_123")
    (.capabilityDirectories builder (java.util.Collections/singletonList "/workspace"))
    (.createdAt builder 100)
    (.files builder (java.util.Collections/emptyList))
    (.name builder "analysis")
    (.network builder network)
    (.object_ builder (JsonValue/from "agent.environment.template"))
    (.packages builder packages)
    (.plugins builder (java.util.Collections/emptyList))
    (.skills builder (java.util.Collections/emptyList))
    (.updatedAt builder 200)
    (.putAdditionalProperty builder "future_field" (JsonValue/from true))
    (.build builder)))

(def expected-template
  {:id "tmpl_123"
   :capability-directories ["/workspace"]
   :created-at 100
   :files []
   :name "analysis"
   :network {:access "restricted" :allowed-domains ["example.com"]}
   :object "agent.environment.template"
   :packages {:npm [] :python ["numpy"] :system []}
   :plugins []
   :skills []
   :updated-at 200
   :future-field true})

(def template-request
  {:name "analysis"
   :capability-directories ["/workspace"]
   :env {:api-mode "test"}
   :files [{:type :inline :path "/workspace/a.txt" :data "YQ=="}]
   :network {:access :restricted :allowed-domains ["example.com"]}
   :packages {:python ["numpy"]}
   :plugins [{:type :inline
              :name "helper"
              :description "A helper"
              :source {:type :base64 :data "eA=="}}]
   :setup-commands [{:command "python -V" :cwd "/workspace"}]
   :skills [{:type :skill-reference :skill-id "skill_123" :version "1"}]
   :future-config {:snake-case :kept-value}})

(deftest creates-environment-template
  (if-let [create (api 'create)]
    (let [captured (atom nil)
          service (proxy [TemplateService] []
                    (create [params]
                      (reset! captured params)
                      (template)))
          result (create (client-for service) template-request)
          ^TemplateCreateParams params @captured
          body (impl/sdk-object->clj (._body params))]
      (testing "converts nested kebab-case request data and preserves additions"
        (is (= "analysis" (:name body)))
        (is (= {:api-mode "test"} (:env body)))
        (is (= "inline" (get-in body [:files 0 :type])))
        (is (= "skill_reference" (get-in body [:skills 0 :type])))
        (is (= {:snake-case "kept_value"} (:future-config body))))
      (is (= expected-template result)))
    (is false "openai.beta.agents.environments.templates/create is not implemented")))

(deftest retrieves-environment-template
  (if-let [retrieve (api 'retrieve)]
    (let [captured (atom nil)
          service (proxy [TemplateService] []
                    (retrieve [params]
                      (reset! captured params)
                      (template)))]
      (is (= expected-template (retrieve (client-for service) "tmpl_123")))
      (is (= "tmpl_123"
             (.get (.environmentTemplateId ^TemplateRetrieveParams @captured))))
      (is (= {:openai/error :missing-key :key :environment-template-id}
             (error-data #(retrieve (client-for service) nil)))))
    (is false "openai.beta.agents.environments.templates/retrieve is not implemented")))

(deftest updates-environment-template
  (if-let [update-template (api 'update)]
    (let [captured (atom nil)
          service (proxy [TemplateService] []
                    (update [params]
                      (reset! captured params)
                      (template)))]
      (is (= expected-template
             (update-template (client-for service) "tmpl_123"
                              {:name "analysis-2"
                               :future-config {:snake-case :kept-value}})))
      (let [^TemplateUpdateParams params @captured
            body (impl/sdk-object->clj (._body params))]
        (is (= "tmpl_123" (.get (.environmentTemplateId params))))
        (is (= "analysis-2" (:name body)))
        (is (= {:snake-case "kept_value"} (:future-config body)))))
    (is false "openai.beta.agents.environments.templates/update is not implemented")))

(deftest lists-environment-templates
  (if-let [list-templates (api 'list)]
    (let [captured (atom [])
          page* (atom nil)
          empty-page* (atom nil)
          service (proxy [TemplateService] []
                    (list [params]
                      (case (count (swap! captured conj params))
                        1 @page*
                        2 @empty-page*
                        (throw (ex-info "Unexpected extra page request" {})))))
          ^TemplateListPageResponse$Builder response-builder
          (TemplateListPageResponse/builder)
          _ (.data response-builder (java.util.Collections/singletonList (template)))
          _ (.firstId response-builder (java.util.Optional/empty))
          _ (.lastId response-builder (java.util.Optional/empty))
          _ (.hasMore response-builder false)
          _ (.object_ response-builder (JsonValue/from "list"))
          response (.build response-builder)
          page (-> (TemplateListPage/builder)
                   (.service service)
                   (.params (TemplateListParams/none))
                   (.response response)
                   (.build))]
      (reset! page* page)
      ;; The SDK stops paging on empty data, even when hasMore is false.
      (reset! empty-page*
              (-> page .toBuilder
                  (.response (-> response .toBuilder
                                 (.data (java.util.Collections/emptyList))
                                 (.build)))
                  (.build)))
      (is (= [expected-template]
             (list-templates (client-for service)
                             {:after "tmpl_100" :limit 10 :order :asc})))
      (is (= 2 (count @captured)))
      (is (= "tmpl_123" (.get (.after ^TemplateListParams (second @captured)))))
      (let [^TemplateListParams params (first @captured)]
        (is (= "tmpl_100" (.get (.after params))))
        (is (= 10 (.get (.limit params))))
        (is (= "asc" (.asString ^TemplateListParams$Order (.get (.order params)))))))
    (is false "openai.beta.agents.environments.templates/list is not implemented")))

(deftest deletes-environment-template
  (if-let [delete-template (api 'delete)]
    (let [captured (atom nil)
          deleted (-> (EnvironmentTemplateDeleted/builder)
                      (.id "tmpl_123")
                      (.deleted true)
                      (.object_ (JsonValue/from "agent.environment.template.deleted"))
                      (.build))
          service (proxy [TemplateService] []
                    (delete [params]
                      (reset! captured params)
                      deleted))]
      (is (= {:id "tmpl_123"
              :deleted true
              :object "agent.environment.template.deleted"}
             (delete-template (client-for service) "tmpl_123")))
      (is (= "tmpl_123"
             (.get (.environmentTemplateId ^TemplateDeleteParams @captured)))))
    (is false "openai.beta.agents.environments.templates/delete is not implemented")))

(deftest validates-template-parameters-and-wraps-api-errors
  (if-let [update-template (api 'update)]
    (let [service (proxy [TemplateService] []
                    (update [_]
                      (throw (OpenAIIoException. "network failed"))))
          client (client-for service)]
      (is (= {:openai/error :missing-key :key :environment-template-id}
             (error-data #(update-template client nil {}))))
      (is (= :io-error
             (:openai/error
              (error-data #(update-template client "tmpl_123" {}))))))
    (is false "openai.beta.agents.environments.templates/update is not implemented")))
