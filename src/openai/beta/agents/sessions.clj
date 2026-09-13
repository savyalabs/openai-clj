(ns openai.beta.agents.sessions
  "Clojure wrapper for the beta Agent Sessions lifecycle API."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.core.http StreamResponse)
           (com.openai.models.beta.agents AgentSession
                                           AgentSessionDeleted
                                           EnvironmentParam)
           (com.openai.models.beta.agents.sessions SessionCreateParams
                                                    SessionCreateParams$Agent
                                                    SessionCreateParams$Agent$Builder
                                                    SessionCreateParams$Agent$ServiceTier
                                                    SessionCreateParams$Input
                                                    SessionCreateParams$Metadata
                                                    SessionDeleteParams
                                                    SessionListPage
                                                    SessionListParams
                                                    SessionListParams$Order
                                                    SessionRetrieveParams
                                                    SessionUpdateParams
                                                    SessionUpdateParams$Metadata)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)))

(set! *warn-on-reflection* true)

(defn- wire-name [x]
  (if (keyword? x)
    (let [n (name x)
          ns (namespace x)]
      (str/replace (if ns (str ns "/" n) n) "-" "_"))
    x))

(defn- ->wire [x]
  (walk/postwalk
   (fn [v]
     (cond
       (keyword? v) (wire-name v)
       (map-entry? v) v
       (map? v) (into {} (map (fn [[k value]] [(wire-name k) value])) v)
       :else v))
   x))

(defn- json-value ^JsonValue [x]
  (JsonValue/from (->wire x)))

(defn- sdk-value [x ^Class target]
  (.convert (json-value x) target))

(defn- normalize-map [x]
  (let [enum-keys #{"object" "role" "status" "type"}]
    (walk/postwalk
     (fn [v]
       (if (map? v)
         (into {}
               (keep (fn [[k value]]
                       (let [n (name k)]
                         (when-not (= n "valid")
                           [(-> n (str/replace "_" "-") keyword)
                            (if (enum-keys n)
                              (some-> value str (str/replace "_" "-") keyword)
                              value)]))))
               v)
         v))
     x)))

(defn- sdk-object->map ^clojure.lang.IPersistentMap [x]
  (normalize-map (impl/sdk-object->clj x)))

(defn- ->environment ^EnvironmentParam [environment]
  (sdk-value environment EnvironmentParam))

(defn- ->agent ^SessionCreateParams$Agent
  [{:keys [instructions model service-tier] :as agent}]
  (let [^SessionCreateParams$Agent$Builder b
        (SessionCreateParams$Agent/builder)]
    (when instructions (.instructions b ^String instructions))
    (when model
      (.model b ^String (if (keyword? model) (name model) (str model))))
    (when service-tier
      (.serviceTier b (SessionCreateParams$Agent$ServiceTier/of
                       (impl/enum-name service-tier))))
    (doseq [[k v] (dissoc agent :instructions :model :service-tier)]
      (.putAdditionalProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- ->input ^SessionCreateParams$Input [input]
  (sdk-value input SessionCreateParams$Input))

(defn- ->create-metadata ^SessionCreateParams$Metadata [metadata]
  (sdk-value metadata SessionCreateParams$Metadata))

(defn- ->update-metadata ^SessionUpdateParams$Metadata [metadata]
  (sdk-value metadata SessionUpdateParams$Metadata))

(defn- ->session-create-params ^SessionCreateParams
  [{:keys [environment agent agent-id input metadata vault-ids] :as req}]
  (when-not environment (impl/missing-key! :environment))
  (when-not (or agent-id agent) (impl/missing-key! :agent-id-or-agent))
  (let [b (SessionCreateParams/builder)]
    (.environment b (->environment environment))
    (when agent (.agent b (->agent agent)))
    (when agent-id (.agentId b ^String agent-id))
    (when input (.input b (->input input)))
    (when metadata (.metadata b (->create-metadata metadata)))
    (when vault-ids (.vaultIds b ^java.util.List (vec vault-ids)))
    (doseq [[k v] (dissoc req :environment :agent :agent-id :input
                          :metadata :vault-ids)]
      (.putAdditionalBodyProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- ->session-retrieve-params ^SessionRetrieveParams [session-id]
  (when-not session-id (impl/missing-key! :session-id))
  (-> (SessionRetrieveParams/builder)
      (.sessionId ^String session-id)
      (.build)))

(defn- ->session-update-params ^SessionUpdateParams
  [session-id {:keys [metadata] :as req}]
  (when-not session-id (impl/missing-key! :session-id))
  (let [b (SessionUpdateParams/builder)]
    (.sessionId b ^String session-id)
    (when metadata (.metadata b (->update-metadata metadata)))
    (doseq [[k v] (dissoc req :metadata)]
      (.putAdditionalBodyProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- ->session-list-params ^SessionListParams
  [{:keys [after agent-id limit order]}]
  (let [b (SessionListParams/builder)]
    (when after (.after b ^String after))
    (when agent-id (.agentId b ^String agent-id))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (SessionListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- ->session-delete-params ^SessionDeleteParams [session-id]
  (when-not session-id (impl/missing-key! :session-id))
  (-> (SessionDeleteParams/builder)
      (.sessionId ^String session-id)
      (.build)))

(defn- sessions-service ^SessionService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)]
    (.sessions agents)))

(defn session-create
  "Create an Agent session and return a normalized Clojure map."
  ^clojure.lang.IPersistentMap [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^SessionService service (sessions-service client)
          ^AgentSession response (.create service (->session-create-params req))]
      (sdk-object->map response))))

(defn session-create-streaming
  "Create an Agent session and return its raw streaming response.

  The caller owns iteration and closing of the returned response."
  ^StreamResponse [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^SessionService service (sessions-service client)]
      (.createStreaming service (->session-create-params req)))))

(defn session-retrieve
  "Retrieve an Agent session by ID as a normalized Clojure map."
  ^clojure.lang.IPersistentMap [^OpenAIClient client session-id]
  (impl/with-api-errors
    (let [^SessionService service (sessions-service client)
          ^AgentSession response
          (.retrieve service (->session-retrieve-params session-id))]
      (sdk-object->map response))))

(defn session-update
  "Update an Agent session and return a normalized Clojure map."
  ^clojure.lang.IPersistentMap [^OpenAIClient client session-id req]
  (impl/with-api-errors
    (let [^SessionService service (sessions-service client)
          ^AgentSession response
          (.update service (->session-update-params session-id req))]
      (sdk-object->map response))))

(defn session-list
  "List Agent sessions as normalized Clojure maps."
  (^clojure.lang.IPersistentVector [^OpenAIClient client]
   (session-list client {}))
  (^clojure.lang.IPersistentVector [^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^SessionService service (sessions-service client)
           ^SessionListPage page (.list service (->session-list-params opts))]
       (mapv sdk-object->map (impl/all-pages page))))))

(defn session-delete
  "Delete an Agent session and return a normalized Clojure map."
  ^clojure.lang.IPersistentMap [^OpenAIClient client session-id]
  (impl/with-api-errors
    (let [^SessionService service (sessions-service client)
          ^AgentSessionDeleted response
          (.delete service (->session-delete-params session-id))]
      (sdk-object->map response))))
