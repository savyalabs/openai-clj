(ns openai.beta.agents.sessions-test
  (:require [clojure.test :refer [deftest is]]
            [openai.beta.agents.sessions :as sessions]
            [openai.core :as openai]
            [openai.impl :as impl]
            [openai.wire-level-test :as wire])
  (:import (com.openai.core JsonNumber JsonObject)
           (com.openai.models.beta.agents EnvironmentParam)
           (com.openai.models.beta.agents.sessions SessionCreateParams SessionUpdateParams)
           (com.sun.net.httpserver HttpExchange)))

(set! *warn-on-reflection* true)

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(deftest validates-session-create-required-fields
  (doseq [[request key]
          [[{} :environment]
           [{:environment {:id "env_123"}} :agent-id-or-agent]]]
    (is (= {:openai/error :missing-key :key key}
           (error-data #(#'sessions/->session-create-params request))))))

(deftest session-create-accepts-prewarmed-environment-id
  (let [params (#'sessions/->session-create-params
                {:environment {:type :openai-hosted :environment-id "env_123"}
                 :agent-id "agent_123"})
        environment (.environment params)]
    (is (instance? EnvironmentParam environment))
    (is (= "env_123"
           (.get (.environmentId (.asOpenAIHosted environment)))))))

(deftest session-create-forwards-removed-spend-control-as-an-unknown-body-property
  (let [params (#'sessions/->session-create-params
                {:environment {:type :openai-hosted :environment-id "env_123"}
                 :agent-id "agent_123"
                 :spend-control {:limit 42}})
        ^SessionCreateParams create-params params
        ^SessionUpdateParams update-params (#'sessions/->session-update-params
                                            "sess_123" {:spend-control {:limit 24}})
        ^JsonObject create-spend-control (get (._additionalBodyProperties create-params) "spend_control")
        ^JsonObject update-spend-control (get (._additionalBodyProperties update-params) "spend_control")
        ^JsonNumber create-limit (get (.values create-spend-control) "limit")
        ^JsonNumber update-limit (get (.values update-spend-control) "limit")]
    (is (= 42 (.value create-limit)))
    (is (= 24 (.value update-limit)))))

(deftest session-prompt-cache-options-and-stream-options-reach-params
  (let [create (#'sessions/->session-create-params
                {:environment {:type :openai-hosted :environment-id "env_123"}
                 :agent-id "agent_123"
                 :prompt-cache-options {:ttl :12h}
                 :stream-options {:include-obfuscation true}})
        update (#'sessions/->session-update-params "sess_123" {:prompt-cache-options {:ttl :30m}})]
    (is (= "12h" (.asString (.get (.ttl (.get (.promptCacheOptions create)))))))
    (is (-> create .streamOptions .get .includeObfuscation .get))
    (is (= "30m" (.asString (.get (.ttl (.get (.promptCacheOptions update)))))))))

(deftest session-review-converts
  (let [session (impl/sdk-input-object
                 {:id "sess_123" :created-at 1 :last-active-at 2
                  :environment {:id "env_123" :status "ready" :type "openai_hosted"}
                  :metadata {} :object "agent_session" :status "idle" :review "auto_review"}
                 com.openai.models.beta.agents.AgentSession)]
    (is (= :auto-review (:review (#'sessions/sdk-object->map session))))))

(deftest session-list-metadata-filter-reaches-params
  (let [params (#'sessions/->session-list-params {:metadata {:team "research"}})]
    (is (= ["research"]
           (vec (.values (._queryParams params) "metadata[team]"))))))

(deftest validates-session-retrieve-requires-id
  (is (= {:openai/error :missing-key :key :session-id}
         (error-data #(#'sessions/->session-retrieve-params nil)))))

(deftest validates-session-update-requires-id
  (is (= {:openai/error :missing-key :key :session-id}
         (error-data #(#'sessions/->session-update-params nil {})))))

(deftest validates-session-delete-requires-id
  (is (= {:openai/error :missing-key :key :session-id}
         (error-data #(#'sessions/->session-delete-params nil)))))

(deftest session-create-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-create))))

(deftest session-create-streaming-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-create-streaming))))

(deftest session-retrieve-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-retrieve))))

(deftest session-update-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-update))))

(deftest session-update-model-passthrough-preserves-reset-semantics-on-the-wire
  ;; Session updates intentionally use additional body properties: a present nil
  ;; becomes JSON null (reset), while an omitted key leaves the field untouched.
  (let [requests (atom [])
        fixture (wire/start-http-fixture!
                 (fn [^HttpExchange exchange]
                   (swap! requests conj (wire/read-bytes exchange))
                   (wire/respond! exchange 400 "application/json"
                                  "{\"error\":{\"message\":\"fixture\",\"type\":\"invalid_request_error\"}}")))
        client (openai/client {:api-key "test-key" :base-url (:base-url fixture)})]
    (try
      (doseq [request [{:model "gpt-4"} {:model nil} {}]]
        (try
          (sessions/session-update client "sess_123" request)
          (catch clojure.lang.ExceptionInfo _)))
      (is (= ["{\"model\":\"gpt-4\"}" "{\"model\":null}" "{}"] @requests))
      (finally (.close client) (wire/stop-http-fixture! fixture)))))

(deftest session-list-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-list))))

(deftest session-delete-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-delete))))
