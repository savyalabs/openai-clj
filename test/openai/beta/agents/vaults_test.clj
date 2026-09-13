(ns openai.beta.agents.vaults-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.vaults Vault
                                                    Vault$Builder
                                                    Vault$Metadata
                                                    Vault$Metadata$Builder
                                                    VaultCreateParams
                                                    VaultDeleteParams
                                                    VaultDeleted
                                                    VaultListPage
                                                    VaultListPageResponse
                                                    VaultListPageResponse$Builder
                                                    VaultListParams
                                                    VaultRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents VaultService)))

(set! *warn-on-reflection* true)

(def mapper (json/object-mapper {:decode-key-fn true}))

(defn- json-value->clj [x]
  (json/read-value (json/write-value-as-string x) mapper))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- wrapper-var [sym]
  (try
    (requiring-resolve sym)
    (catch java.io.FileNotFoundException _
      nil)))

(defn- response-vault [id name]
  (let [^Vault$Metadata$Builder metadata (Vault$Metadata/builder)
        ^Vault$Builder vault (Vault/builder)]
    (.putAdditionalProperty metadata "environment" (JsonValue/from "test"))
    (.id vault ^String id)
    (.createdAt vault 1700000000)
    (.metadata vault (.build metadata))
    (.name vault ^String name)
    (.object_ vault (JsonValue/from "vault"))
    (.build vault)))

(defn- client-for [^VaultService vault-service]
  (let [agents (proxy [AgentService] []
                 (vaults [] vault-service))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(deftest creates-vault
  (let [create-vault (wrapper-var 'openai.beta.agents.vaults/create-vault)]
    (if create-vault
      (let [captured (atom nil)
            response (response-vault "vault_1" "Test vault")
            service (proxy [VaultService] []
                      (create [p] (reset! captured p) response))
            request {:name "Test vault"
                     :metadata {:environment "test"
                                :retention-days 30}}
            result (create-vault (client-for service) request)
            ^VaultCreateParams params @captured]
        (testing "request parameters"
          (is (= "Test vault" (.get (.name params))))
          (is (= {:environment "test" :retention-days 30}
                 (json-value->clj (.get (.metadata params))))))
        (testing "response"
          (is (= {:id "vault_1"
                  :created-at 1700000000
                  :metadata {:environment "test"}
                  :name "Test vault"}
                 result))))
      (is false "openai.beta.agents.vaults/create-vault is not implemented"))))

(deftest retrieves-vault
  (let [retrieve-vault (wrapper-var 'openai.beta.agents.vaults/retrieve-vault)]
    (if retrieve-vault
      (let [captured (atom nil)
            response (response-vault "vault_1" "Test vault")
            service (proxy [VaultService] []
                      (retrieve [p] (reset! captured p) response))
            result (retrieve-vault (client-for service) "vault_1")]
        (is (= "vault_1"
               (.get (.vaultId ^VaultRetrieveParams @captured))))
        (is (= "vault_1" (:id result)))
        (is (= {:openai/error :missing-key :key :vault-id}
               (error-data #(retrieve-vault (client-for service) nil)))))
      (is false "openai.beta.agents.vaults/retrieve-vault is not implemented"))))

(deftest lists-vaults
  (let [list-vaults (wrapper-var 'openai.beta.agents.vaults/list-vaults)]
    (if list-vaults
      (let [captured (atom [])
            calls (atom 0)
            page* (atom nil)
            empty-page* (atom nil)
            service (proxy [VaultService] []
                      (list [p]
                        (swap! captured conj p)
                        (if (= 1 (swap! calls inc)) @page* @empty-page*)))
            params (VaultListParams/none)
            page (-> (VaultListPage/builder)
                     (.service service)
                     (.params params)
                     (.response (let [^VaultListPageResponse$Builder response
                                      (VaultListPageResponse/builder)
                                      ^java.util.List data
                                      [(response-vault "vault_1" "First")
                                       (response-vault "vault_2" "Second")]]
                                  (.data response data)
                                  (.firstId response "vault_1")
                                  (.hasMore response false)
                                  (.lastId response "vault_2")
                                  (.object_ response (JsonValue/from "list"))
                                  (.build response)))
                     (.build))
            empty-page (-> (VaultListPage/builder)
                           (.service service)
                           (.params params)
                           (.response
                            (let [^VaultListPageResponse$Builder response
                                  (VaultListPageResponse/builder)
                                  ^java.util.List data []]
                              (.data response data)
                              (.firstId response (java.util.Optional/empty))
                              (.hasMore response false)
                              (.lastId response (java.util.Optional/empty))
                              (.object_ response (JsonValue/from "list"))
                              (.build response)))
                           (.build))]
        (reset! page* page)
        (reset! empty-page* empty-page)
        (is (= [{:id "vault_1" :created-at 1700000000
                 :metadata {:environment "test"} :name "First"}
                {:id "vault_2" :created-at 1700000000
                 :metadata {:environment "test"} :name "Second"}]
               (list-vaults (client-for service)
                            {:after "vault_0" :limit 2 :order :desc})))
        (let [^VaultListParams actual (first @captured)]
          (is (= "vault_0" (.get (.after actual))))
          (is (= 2 (.get (.limit actual))))
          (is (= "desc" (-> actual .order .get .toString)))))
      (is false "openai.beta.agents.vaults/list-vaults is not implemented"))))

(deftest deletes-vault
  (let [delete-vault (wrapper-var 'openai.beta.agents.vaults/delete-vault)]
    (if delete-vault
      (let [captured (atom nil)
            response (-> (VaultDeleted/builder)
                         (.id "vault_1")
                         (.deleted true)
                         (.object_ (JsonValue/from "vault.deleted"))
                         (.build))
            service (proxy [VaultService] []
                      (delete [p] (reset! captured p) response))
            result (delete-vault (client-for service) "vault_1")]
        (is (= {:id "vault_1" :deleted true} result))
        (is (= "vault_1"
               (.get (.vaultId ^VaultDeleteParams @captured))))
        (is (= {:openai/error :missing-key :key :vault-id}
               (error-data #(delete-vault (client-for service) nil)))))
      (is false "openai.beta.agents.vaults/delete-vault is not implemented"))))
