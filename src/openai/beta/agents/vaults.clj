(ns openai.beta.agents.vaults
  "Clojure wrapper for the beta Agents API vault operations."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents.vaults Vault
                                                    VaultCreateParams
                                                    VaultCreateParams$Builder
                                                    VaultCreateParams$Metadata
                                                    VaultCreateParams$Metadata$Builder
                                                    VaultDeleteParams
                                                    VaultListPage
                                                    VaultListParams
                                                    VaultListParams$Order
                                                    VaultRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents VaultService)))

(set! *warn-on-reflection* true)

(defn- vault-service ^VaultService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)]
    (.vaults agents)))

(defn- ->metadata ^VaultCreateParams$Metadata [metadata]
  (let [^VaultCreateParams$Metadata$Builder b (VaultCreateParams$Metadata/builder)]
    (.putAllAdditionalProperties b (impl/->json-value-properties metadata))
    (.build b)))

(defn- ->vault-create-params ^VaultCreateParams [{:keys [name metadata]}]
  (let [^VaultCreateParams$Builder b (VaultCreateParams/builder)]
    (when name (.name b ^String name))
    (when metadata (.metadata b (->metadata metadata)))
    (.build b)))

(defn- ->vault-retrieve-params ^VaultRetrieveParams [^String vault-id]
  (when-not vault-id (impl/missing-key! :vault-id))
  (-> (VaultRetrieveParams/builder)
      (.vaultId vault-id)
      (.build)))

(defn- ->vault-list-params ^VaultListParams [{:keys [after limit order]}]
  (let [b (VaultListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (VaultListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- ->vault-delete-params ^VaultDeleteParams [^String vault-id]
  (when-not vault-id (impl/missing-key! :vault-id))
  (-> (VaultDeleteParams/builder)
      (.vaultId vault-id)
      (.build)))

(defn- vault->map [^Vault vault]
  (cond-> {:id (.id vault)
           :created-at (.createdAt vault)
           :metadata (impl/sdk-object->clj (.metadata vault))}
    (.isPresent (.name vault)) (assoc :name (impl/opt-get (.name vault)))))

(defn create-vault
  "Create a beta Agents API vault."
  ([^OpenAIClient client]
   (create-vault client {}))
  ([^OpenAIClient client req]
   (impl/with-api-errors
     (vault->map (.create (vault-service client)
                          (->vault-create-params req))))))

(defn retrieve-vault
  "Retrieve a beta Agents API vault by ID."
  [^OpenAIClient client ^String vault-id]
  (impl/with-api-errors
    (vault->map (.retrieve (vault-service client)
                           (->vault-retrieve-params vault-id)))))

(defn list-vaults
  "List beta Agents API vaults."
  ([^OpenAIClient client]
   (list-vaults client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^VaultListPage page
           (.list (vault-service client) (->vault-list-params opts))]
       (mapv vault->map (impl/all-pages page))))))

(defn delete-vault
  "Delete a beta Agents API vault by ID."
  [^OpenAIClient client ^String vault-id]
  (impl/with-api-errors
    (let [response (.delete (vault-service client)
                            (->vault-delete-params vault-id))]
      {:id (.id response)
       :deleted (.deleted response)})))
