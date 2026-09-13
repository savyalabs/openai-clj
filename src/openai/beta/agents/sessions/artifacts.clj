(ns openai.beta.agents.sessions.artifacts
  "Clojure wrapper for beta Agent Session artifacts.

  ArtifactService exposes four operations in openai-java 4.63.1: `retrieve`
  returns a SessionArtifact by session and artifact IDs; `list` returns a
  paginated ArtifactListPage filtered by session ID and optional cursor,
  environment, limit, and order; `delete` returns SessionArtifactDeleted; and
  `content` returns a raw HttpResponse whose body contains the artifact bytes."
  (:refer-clojure :exclude [list])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http HttpResponse)
           (com.openai.models.beta.agents.sessions.artifacts ArtifactContentParams
                                                              ArtifactDeleteParams
                                                              ArtifactListPage
                                                              ArtifactListParams
                                                              ArtifactListParams$Builder
                                                              ArtifactListParams$Order
                                                              ArtifactRetrieveParams
                                                              SessionArtifact
                                                              SessionArtifactDeleted)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions ArtifactService)))

(set! *warn-on-reflection* true)

(defn- artifact-service ^ArtifactService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)
        ^SessionService sessions (.sessions agents)]
    (.artifacts sessions)))

(defn- require-ids! [session-id artifact-id]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not artifact-id (impl/missing-key! :artifact-id)))

(defn ->retrieve-params ^ArtifactRetrieveParams
  [^String session-id ^String artifact-id]
  (require-ids! session-id artifact-id)
  (-> (ArtifactRetrieveParams/builder)
      (.sessionId session-id)
      (.artifactId artifact-id)
      (.build)))

(defn ->list-params ^ArtifactListParams
  [^String session-id {:keys [after environment-id limit order]}]
  (when-not session-id (impl/missing-key! :session-id))
  (let [^ArtifactListParams$Builder b (ArtifactListParams/builder)]
    (.sessionId b session-id)
    (when after (.after b ^String after))
    (when environment-id (.environmentId b ^String environment-id))
    (when limit (.limit b (long limit)))
    (when order (.order b (ArtifactListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn ->delete-params ^ArtifactDeleteParams
  [^String session-id ^String artifact-id]
  (require-ids! session-id artifact-id)
  (-> (ArtifactDeleteParams/builder)
      (.sessionId session-id)
      (.artifactId artifact-id)
      (.build)))

(defn ->content-params ^ArtifactContentParams
  [^String session-id ^String artifact-id]
  (require-ids! session-id artifact-id)
  (-> (ArtifactContentParams/builder)
      (.sessionId session-id)
      (.artifactId artifact-id)
      (.build)))

(defn- artifact->map ^clojure.lang.IPersistentMap [^SessionArtifact artifact]
  (impl/sdk-object->clj artifact))

(defn- deleted->map ^clojure.lang.IPersistentMap [^SessionArtifactDeleted deleted]
  (impl/sdk-object->clj deleted))

(defn retrieve ^clojure.lang.IPersistentMap
  [^OpenAIClient client ^String session-id ^String artifact-id]
  (impl/with-api-errors
    (artifact->map
     (.retrieve (artifact-service client)
                (->retrieve-params session-id artifact-id)))))

(defn list
  "List all artifacts for an Agent Session. Opts accepts `:after`,
  `:environment-id`, `:limit`, and `:order`."
  (^clojure.lang.IPersistentVector [^OpenAIClient client ^String session-id]
   (list client session-id {}))
  (^clojure.lang.IPersistentVector [^OpenAIClient client ^String session-id opts]
   (impl/with-api-errors
     (let [^ArtifactListPage page
           (.list (artifact-service client) (->list-params session-id opts))]
       (mapv artifact->map (impl/all-pages page))))))

(defn delete ^clojure.lang.IPersistentMap
  [^OpenAIClient client ^String session-id ^String artifact-id]
  (impl/with-api-errors
    (deleted->map
     (.delete (artifact-service client)
              (->delete-params session-id artifact-id)))))

(defn content
  "Download an Agent Session artifact as a byte array."
  ^bytes [^OpenAIClient client ^String session-id ^String artifact-id]
  (impl/with-api-errors
    (with-open [^HttpResponse response
                (.content (artifact-service client)
                          (->content-params session-id artifact-id))]
      (.readAllBytes (.body response)))))
