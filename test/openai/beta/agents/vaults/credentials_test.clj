(ns openai.beta.agents.vaults.credentials-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.vaults.credentials Credential
                                                                Credential$Builder
                                                                CredentialAuth$StaticBearer
                                                                CredentialAuth$StaticBearer$Builder
                                                                CredentialCreateParams
                                                                CredentialDeleteParams
                                                                CredentialDeleted
                                                                CredentialListPage
                                                                CredentialListPageResponse
                                                                CredentialListPageResponse$Builder
                                                                CredentialListParams
                                                                CredentialRetrieveParams
                                                                CredentialUpdateParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents VaultService)
           (com.openai.services.blocking.beta.agents.vaults CredentialService)))

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

(defn- response-credential [id name]
  (let [^CredentialAuth$StaticBearer$Builder auth
        (CredentialAuth$StaticBearer/builder)
        ^Credential$Builder credential (Credential/builder)]
    (.mcpServerUrl auth "https://example.test/mcp")
    (.id credential ^String id)
    (.auth credential (.build auth))
    (.createdAt credential 1700000000)
    (.name credential ^String name)
    (.object_ credential (JsonValue/from "vault.credential"))
    (.updatedAt credential 1700000100)
    (.vaultId credential "vault_1")
    (.build credential)))

(defn- client-for [^CredentialService credential-service]
  (let [vaults (proxy [VaultService] []
                 (credentials [] credential-service))
        agents (proxy [AgentService] []
                 (vaults [] vaults))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(deftest creates-credential
  (let [create-credential
        (wrapper-var 'openai.beta.agents.vaults.credentials/create-credential)]
    (if create-credential
      (let [captured (atom [])
            response (response-credential "cred_1" "Test credential")
            service (proxy [CredentialService] []
                      (create [p] (swap! captured conj p) response))
            client (client-for service)
            result (create-credential
                    client
                    "vault_1"
                    {:name "Test credential"
                     :auth {:type :static-bearer
                            :token "test-secret-value"
                            :mcp-server-url "https://example.test/mcp"}})]
        (create-credential
         client
         "vault_1"
         {:name "OAuth credential"
          :auth {:type :mcp-oauth
                 :access-token "test-access-token"
                 :mcp-server-url "https://example.test/mcp"
                 :expires-at "2099-01-01T00:00:00Z"
                 :refresh {:client-id "test-client-id"
                           :refresh-token "test-refresh-token"
                           :token-endpoint "https://example.test/token"
                           :token-endpoint-auth {:type :client-secret-post
                                                 :client-secret "test-client-secret"}}}})
        (testing "static bearer request"
          (let [^CredentialCreateParams params (first @captured)
                auth (json-value->clj (.auth params))]
            (is (= "vault_1" (.get (.vaultId params))))
            (is (= "Test credential" (.name params)))
            (is (= "static_bearer" (:type auth)))
            (is (= "test-secret-value" (:token auth)))
            (is (= "https://example.test/mcp" (:mcp_server_url auth)))))
        (testing "MCP OAuth request remains schema-forward-compatible"
          (let [^CredentialCreateParams params (second @captured)
                auth (json-value->clj (.auth params))]
            (is (= "mcp_oauth" (:type auth)))
            (is (= "test-access-token" (:access_token auth)))
            (is (= "test-refresh-token" (get-in auth [:refresh :refresh_token])))
            (is (= "client_secret_post"
                   (get-in auth [:refresh :token_endpoint_auth :type])))))
        (testing "response"
          (is (= {:id "cred_1"
                  :auth {:type :static-bearer
                         :mcp-server-url "https://example.test/mcp"}
                  :created-at 1700000000
                  :name "Test credential"
                  :updated-at 1700000100
                  :vault-id "vault_1"}
                 result)))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :vault-id}
                 (error-data #(create-credential client nil
                                                 {:name "Test" :auth {}}))))
          (is (= {:openai/error :missing-key :key :name}
                 (error-data #(create-credential client "vault_1"
                                                 {:auth {}}))))
          (is (= {:openai/error :missing-key :key :auth}
                 (error-data #(create-credential client "vault_1"
                                                 {:name "Test"}))))))
      (is false
          "openai.beta.agents.vaults.credentials/create-credential is not implemented"))))

(deftest retrieves-credential
  (let [retrieve-credential
        (wrapper-var 'openai.beta.agents.vaults.credentials/retrieve-credential)]
    (if retrieve-credential
      (let [captured (atom nil)
            response (response-credential "cred_1" "Test credential")
            service (proxy [CredentialService] []
                      (retrieve [p] (reset! captured p) response))
            client (client-for service)
            result (retrieve-credential client "vault_1" "cred_1")
            ^CredentialRetrieveParams params @captured]
        (is (= "vault_1" (.vaultId params)))
        (is (= "cred_1" (.get (.credentialId params))))
        (is (= "cred_1" (:id result)))
        (is (= {:openai/error :missing-key :key :vault-id}
               (error-data #(retrieve-credential client nil "cred_1"))))
        (is (= {:openai/error :missing-key :key :credential-id}
               (error-data #(retrieve-credential client "vault_1" nil)))))
      (is false
          "openai.beta.agents.vaults.credentials/retrieve-credential is not implemented"))))

(deftest updates-credential
  (let [update-credential
        (wrapper-var 'openai.beta.agents.vaults.credentials/update-credential)]
    (if update-credential
      (let [captured (atom nil)
            response (response-credential "cred_1" "Test credential")
            service (proxy [CredentialService] []
                      (update [p] (reset! captured p) response))
            client (client-for service)
            result (update-credential
                    client "vault_1" "cred_1"
                    {:auth {:type :static-bearer
                            :token "test-rotated-secret"}})
            ^CredentialUpdateParams params @captured
            auth (json-value->clj (.auth params))]
        (is (= "vault_1" (.vaultId params)))
        (is (= "cred_1" (.get (.credentialId params))))
        (is (= {:type "static_bearer" :token "test-rotated-secret"} auth))
        (is (= "cred_1" (:id result)))
        (is (= {:openai/error :missing-key :key :vault-id}
               (error-data #(update-credential client nil "cred_1" {:auth {}}))))
        (is (= {:openai/error :missing-key :key :credential-id}
               (error-data #(update-credential client "vault_1" nil {:auth {}}))))
        (is (= {:openai/error :missing-key :key :auth}
               (error-data #(update-credential client "vault_1" "cred_1" {})))))
      (is false
          "openai.beta.agents.vaults.credentials/update-credential is not implemented"))))

(deftest lists-credentials
  (let [list-credentials
        (wrapper-var 'openai.beta.agents.vaults.credentials/list-credentials)]
    (if list-credentials
      (let [captured (atom [])
            calls (atom 0)
            page* (atom nil)
            empty-page* (atom nil)
            service (proxy [CredentialService] []
                      (list [p]
                        (swap! captured conj p)
                        (if (= 1 (swap! calls inc)) @page* @empty-page*)))
            params (CredentialListParams/builder)
            _ (.vaultId params "vault_1")
            params (.build params)
            page (-> (CredentialListPage/builder)
                     (.service service)
                     (.params params)
                     (.response
                      (let [^CredentialListPageResponse$Builder response
                            (CredentialListPageResponse/builder)
                            ^java.util.List data
                            [(response-credential "cred_1" "First")
                             (response-credential "cred_2" "Second")]]
                        (.data response data)
                        (.firstId response "cred_1")
                        (.hasMore response false)
                        (.lastId response "cred_2")
                        (.object_ response (JsonValue/from "list"))
                        (.build response)))
                     (.build))
            empty-page (-> (CredentialListPage/builder)
                           (.service service)
                           (.params params)
                           (.response
                            (let [^CredentialListPageResponse$Builder response
                                  (CredentialListPageResponse/builder)
                                  ^java.util.List data []]
                              (.data response data)
                              (.firstId response (java.util.Optional/empty))
                              (.hasMore response false)
                              (.lastId response (java.util.Optional/empty))
                              (.object_ response (JsonValue/from "list"))
                              (.build response)))
                           (.build))
            client (client-for service)]
        (reset! page* page)
        (reset! empty-page* empty-page)
        (is (= ["cred_1" "cred_2"]
               (mapv :id (list-credentials client "vault_1"
                                           {:after "cred_0"
                                            :limit 2
                                            :order :asc}))))
        (let [^CredentialListParams actual (first @captured)]
          (is (= "vault_1" (.get (.vaultId actual))))
          (is (= "cred_0" (.get (.after actual))))
          (is (= 2 (.get (.limit actual))))
          (is (= "asc" (-> actual .order .get .toString))))
        (is (= {:openai/error :missing-key :key :vault-id}
               (error-data #(list-credentials client nil {})))))
      (is false
          "openai.beta.agents.vaults.credentials/list-credentials is not implemented"))))

(deftest deletes-credential
  (let [delete-credential
        (wrapper-var 'openai.beta.agents.vaults.credentials/delete-credential)]
    (if delete-credential
      (let [captured (atom nil)
            response (-> (CredentialDeleted/builder)
                         (.id "cred_1")
                         (.deleted true)
                         (.object_ (JsonValue/from "vault.credential.deleted"))
                         (.build))
            service (proxy [CredentialService] []
                      (delete [p] (reset! captured p) response))
            client (client-for service)
            result (delete-credential client "vault_1" "cred_1")
            ^CredentialDeleteParams params @captured]
        (is (= {:id "cred_1" :deleted true} result))
        (is (= "vault_1" (.vaultId params)))
        (is (= "cred_1" (.get (.credentialId params))))
        (is (= {:openai/error :missing-key :key :vault-id}
               (error-data #(delete-credential client nil "cred_1"))))
        (is (= {:openai/error :missing-key :key :credential-id}
               (error-data #(delete-credential client "vault_1" nil)))))
      (is false
          "openai.beta.agents.vaults.credentials/delete-credential is not implemented"))))
