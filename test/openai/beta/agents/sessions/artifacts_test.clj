(ns openai.beta.agents.sessions.artifacts-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (java.io ByteArrayInputStream)
           (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.sessions.artifacts ArtifactContentParams
                                                              ArtifactDeleteParams
                                                              ArtifactListPage
                                                              ArtifactListPageResponse
                                                              ArtifactListPageResponse$Builder
                                                              ArtifactListParams
                                                              ArtifactListParams$Order
                                                              ArtifactRetrieveParams
                                                              SessionArtifact
                                                              SessionArtifactDeleted)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions ArtifactService)))

(set! *warn-on-reflection* true)

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- implementation-var [symbol]
  (try
    (requiring-resolve symbol)
    (catch java.io.FileNotFoundException _
      nil)))

(defn- client-for [^ArtifactService artifact-service]
  (let [sessions (proxy [SessionService] []
                   (artifacts [] artifact-service))
        agents (proxy [AgentService] []
                 (sessions [] sessions))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(defn- artifact [id]
  (-> (SessionArtifact/builder)
      (.id ^String id)
      (.createdAt 123)
      (.environmentId "env_1")
      (.object_ (JsonValue/from "agent.session.artifact"))
      (.path "/tmp/report.txt")
      (.sessionId "sess_1")
      (.sizeBytes 42)
      (.turnId "turn_1")
      (.build)))

(def artifact-map
  {:id "art_1"
   :created-at 123
   :environment-id "env_1"
   :object "agent.session.artifact"
   :path "/tmp/report.txt"
   :session-id "sess_1"
   :size-bytes 42
   :turn-id "turn_1"})

(deftest retrieves-session-artifact
  (let [build-params (implementation-var
                      'openai.beta.agents.sessions.artifacts/->retrieve-params)
        retrieve (implementation-var
                  'openai.beta.agents.sessions.artifacts/retrieve)]
    (if (and build-params retrieve)
      (let [^ArtifactRetrieveParams params (build-params "sess_1" "art_1")
            captured (atom nil)
            artifact-service (proxy [ArtifactService] []
                               (retrieve [p]
                                 (reset! captured p)
                                 (artifact "art_1")))
            client (client-for artifact-service)]
        (is (= "sess_1" (.sessionId params)))
        (is (= "art_1" (.get (.artifactId params))))
        (is (= artifact-map (retrieve client "sess_1" "art_1")))
        (is (= "art_1"
               (-> ^ArtifactRetrieveParams @captured .artifactId .get)))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(retrieve client nil "art_1"))))
        (is (= {:openai/error :missing-key :key :artifact-id}
               (error-data #(retrieve client "sess_1" nil)))))
      (is false "openai.beta.agents.sessions.artifacts/retrieve is not implemented"))))

(deftest lists-session-artifacts
  (let [build-params (implementation-var
                      'openai.beta.agents.sessions.artifacts/->list-params)
        list-artifacts (implementation-var
                        'openai.beta.agents.sessions.artifacts/list)]
    (if (and build-params list-artifacts)
      (let [opts {:after "art_0" :environment-id "env_1" :limit 10 :order :desc}
            ^ArtifactListParams params (build-params "sess_1" opts)
            captured (atom nil)
            service-ref (atom nil)
            ^java.util.ArrayList artifact-data (java.util.ArrayList.)
            _ (.add artifact-data (artifact "art_1"))
            ^ArtifactListPageResponse$Builder response-builder
            (ArtifactListPageResponse/builder)
            _ (.data response-builder artifact-data)
            _ (.firstId response-builder "art_1")
            _ (.hasMore response-builder false)
            _ (.lastId response-builder "art_1")
            _ (.object_ response-builder (JsonValue/from "list"))
            response (.build response-builder)
            artifact-service
            (proxy [ArtifactService] []
              (list [p]
                (reset! captured p)
                (-> (ArtifactListPage/builder)
                    (.service ^ArtifactService @service-ref)
                    (.params ^ArtifactListParams p)
                    (.response response)
                    (.build))))
            _ (reset! service-ref artifact-service)
            client (client-for artifact-service)]
        (testing "request parameters"
          (is (= "sess_1" (.get (.sessionId params))))
          (is (= "art_0" (.get (.after params))))
          (is (= "env_1" (.get (.environmentId params))))
          (is (= 10 (.get (.limit params))))
          (is (= "desc" (.asString ^ArtifactListParams$Order
                                    (.get (.order params))))))
        (testing "pagination and response mapping"
          (is (= [artifact-map] (list-artifacts client "sess_1" opts)))
          (is (= "env_1" (-> ^ArtifactListParams @captured
                              .environmentId .get))))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(list-artifacts client nil)))))
      (is false "openai.beta.agents.sessions.artifacts/list is not implemented"))))

(deftest deletes-session-artifact
  (let [build-params (implementation-var
                      'openai.beta.agents.sessions.artifacts/->delete-params)
        delete (implementation-var 'openai.beta.agents.sessions.artifacts/delete)]
    (if (and build-params delete)
      (let [^ArtifactDeleteParams params (build-params "sess_1" "art_1")
            captured (atom nil)
            response (-> (SessionArtifactDeleted/builder)
                         (.id "art_1")
                         (.deleted true)
                         (.object_ (JsonValue/from "agent.session.artifact.deleted"))
                         (.build))
            artifact-service (proxy [ArtifactService] []
                               (delete [p] (reset! captured p) response))
            client (client-for artifact-service)]
        (is (= "sess_1" (.sessionId params)))
        (is (= "art_1" (.get (.artifactId params))))
        (is (= {:id "art_1"
                :deleted true
                :object "agent.session.artifact.deleted"}
               (delete client "sess_1" "art_1")))
        (is (= "art_1" (-> ^ArtifactDeleteParams @captured .artifactId .get)))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(delete client nil "art_1"))))
        (is (= {:openai/error :missing-key :key :artifact-id}
               (error-data #(delete client "sess_1" nil)))))
      (is false "openai.beta.agents.sessions.artifacts/delete is not implemented"))))

(deftest downloads-session-artifact-content
  (let [build-params (implementation-var
                      'openai.beta.agents.sessions.artifacts/->content-params)
        content (implementation-var 'openai.beta.agents.sessions.artifacts/content)]
    (if (and build-params content)
      (let [^ArtifactContentParams params (build-params "sess_1" "art_1")
            captured (atom nil)
            closed? (atom false)
            response (proxy [com.openai.core.http.HttpResponse] []
                       (statusCode [] 200)
                       (headers [] nil)
                       (body [] (ByteArrayInputStream. (byte-array [1 2 3 4])))
                       (close [] (reset! closed? true)))
            artifact-service (proxy [ArtifactService] []
                               (content [p] (reset! captured p) response))
            client (client-for artifact-service)]
        (is (= "sess_1" (.sessionId params)))
        (is (= "art_1" (.get (.artifactId params))))
        (is (= [1 2 3 4] (vec (content client "sess_1" "art_1"))))
        (is (= "art_1" (-> ^ArtifactContentParams @captured .artifactId .get)))
        (is @closed?)
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(content client nil "art_1"))))
        (is (= {:openai/error :missing-key :key :artifact-id}
               (error-data #(content client "sess_1" nil)))))
      (is false "openai.beta.agents.sessions.artifacts/content is not implemented"))))
