(ns openai.beta.agents.sessions-test
  (:require [clojure.test :refer [deftest is]]
            [openai.beta.agents.sessions :as sessions]
            [openai.core :as openai]
            [openai.wire-level-test :as wire])
  (:import (com.sun.net.httpserver HttpExchange)))

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
