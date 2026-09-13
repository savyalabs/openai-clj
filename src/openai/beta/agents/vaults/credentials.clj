(ns openai.beta.agents.vaults.credentials
  "Clojure wrapper for the beta Agents API vault credential operations."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.vaults.credentials Credential
                                                                CredentialAuthCreateParam
                                                                CredentialAuthRotateParam
                                                                CredentialCreateParams
                                                                CredentialCreateParams$Builder
                                                                CredentialDeleteParams
                                                                CredentialListPage
                                                                CredentialListParams
                                                                CredentialListParams$Builder
                                                                CredentialListParams$Order
                                                                CredentialRetrieveParams
                                                                CredentialUpdateParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents VaultService)
           (com.openai.services.blocking.beta.agents.vaults CredentialService)
           (java.lang.reflect Constructor)))

(set! *warn-on-reflection* true)

(defn- credential-service ^CredentialService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)
        ^VaultService vaults (.vaults agents)]
    (.credentials vaults)))

(defn- auth->json-value ^JsonValue [auth]
  (JsonValue/from
   (letfn [(json-compatible [x]
             (cond
               (map? x) (into {}
                              (map (fn [[k v]]
                                     [(impl/enum-name k) (json-compatible v)]))
                              x)
               (sequential? x) (mapv json-compatible x)
               (keyword? x) (impl/enum-name x)
               :else x))]
     (json-compatible auth))))

(defn- raw-union-constructor ^Constructor [^Class union-class]
  (let [^Constructor constructor
        (first (filter #(= 3 (alength (.getParameterTypes ^Constructor %)))
                       (.getDeclaredConstructors union-class)))]
    (.setAccessible constructor true)
    constructor))

(def ^:private ^Constructor credential-auth-create-constructor
  (raw-union-constructor CredentialAuthCreateParam))

(def ^:private ^Constructor credential-auth-rotate-constructor
  (raw-union-constructor CredentialAuthRotateParam))

(defn- raw-auth-param [^Constructor constructor auth]
  ;; Known auth variants expose their internal `isValid` property when serialized
  ;; by a general-purpose Jackson mapper. The union's raw branch serializes only
  ;; API fields and also keeps the wrapper forward-compatible with new auth shapes.
  (.newInstance constructor
                (object-array [nil nil (auth->json-value auth)])))

(defn- ->credential-auth-create-param ^CredentialAuthCreateParam [auth]
  (raw-auth-param credential-auth-create-constructor auth))

(defn- ->credential-auth-rotate-param ^CredentialAuthRotateParam [auth]
  (raw-auth-param credential-auth-rotate-constructor auth))

(defn- ->credential-create-params ^CredentialCreateParams
  [^String vault-id {:keys [name auth]}]
  (when-not vault-id (impl/missing-key! :vault-id))
  (when-not name (impl/missing-key! :name))
  (when-not auth (impl/missing-key! :auth))
  (let [^CredentialCreateParams$Builder b (CredentialCreateParams/builder)]
    (.vaultId b vault-id)
    (.name b ^String name)
    (.auth b (->credential-auth-create-param auth))
    (.build b)))

(defn- ->credential-retrieve-params ^CredentialRetrieveParams
  [^String vault-id ^String credential-id]
  (when-not vault-id (impl/missing-key! :vault-id))
  (when-not credential-id (impl/missing-key! :credential-id))
  (-> (CredentialRetrieveParams/builder)
      (.vaultId vault-id)
      (.credentialId credential-id)
      (.build)))

(defn- ->credential-update-params ^CredentialUpdateParams
  [^String vault-id ^String credential-id {:keys [auth]}]
  (when-not vault-id (impl/missing-key! :vault-id))
  (when-not credential-id (impl/missing-key! :credential-id))
  (when-not auth (impl/missing-key! :auth))
  (-> (CredentialUpdateParams/builder)
      (.vaultId vault-id)
      (.credentialId credential-id)
      (.auth (->credential-auth-rotate-param auth))
      (.build)))

(defn- ->credential-list-params ^CredentialListParams
  [^String vault-id {:keys [after limit order]}]
  (when-not vault-id (impl/missing-key! :vault-id))
  (let [^CredentialListParams$Builder b (CredentialListParams/builder)]
    (.vaultId b vault-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (CredentialListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- ->credential-delete-params ^CredentialDeleteParams
  [^String vault-id ^String credential-id]
  (when-not vault-id (impl/missing-key! :vault-id))
  (when-not credential-id (impl/missing-key! :credential-id))
  (-> (CredentialDeleteParams/builder)
      (.vaultId vault-id)
      (.credentialId credential-id)
      (.build)))

(defn- keywordize-auth-types [x]
  (cond
    (map? x) (let [m (into {}
                           (map (fn [[k v]] [k (keywordize-auth-types v)]))
                           x)]
               (cond-> m
                 (string? (:type m)) (update :type impl/->keyword)))
    (vector? x) (mapv keywordize-auth-types x)
    :else x))

(defn- credential->map [^Credential credential]
  {:id (.id credential)
   :auth (keywordize-auth-types (impl/sdk-object->clj (.auth credential)))
   :created-at (.createdAt credential)
   :name (.name credential)
   :updated-at (.updatedAt credential)
   :vault-id (.vaultId credential)})

(defn create-credential
  "Create a credential in a beta Agents API vault."
  [^OpenAIClient client ^String vault-id req]
  (impl/with-api-errors
    (credential->map
     (.create (credential-service client)
              (->credential-create-params vault-id req)))))

(defn retrieve-credential
  "Retrieve a credential from a beta Agents API vault."
  [^OpenAIClient client ^String vault-id ^String credential-id]
  (impl/with-api-errors
    (credential->map
     (.retrieve (credential-service client)
                (->credential-retrieve-params vault-id credential-id)))))

(defn update-credential
  "Update a credential in a beta Agents API vault."
  [^OpenAIClient client ^String vault-id ^String credential-id req]
  (impl/with-api-errors
    (credential->map
     (.update (credential-service client)
              (->credential-update-params vault-id credential-id req)))))

(defn list-credentials
  "List credentials in a beta Agents API vault."
  ([^OpenAIClient client ^String vault-id]
   (list-credentials client vault-id {}))
  ([^OpenAIClient client ^String vault-id opts]
   (impl/with-api-errors
     (let [^CredentialListPage page
           (.list (credential-service client)
                  (->credential-list-params vault-id opts))]
       (mapv credential->map (impl/all-pages page))))))

(defn delete-credential
  "Delete a credential from a beta Agents API vault."
  [^OpenAIClient client ^String vault-id ^String credential-id]
  (impl/with-api-errors
    (let [response (.delete (credential-service client)
                            (->credential-delete-params vault-id credential-id))]
      {:id (.id response)
       :deleted (.deleted response)})))
