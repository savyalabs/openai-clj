(ns openai.beta.agents.sessions-test
  (:require [clojure.test :refer [deftest is]]
            [openai.beta.agents.sessions :as sessions]))

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

(deftest session-list-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-list))))

(deftest session-delete-function-exists
  (is (some? (ns-resolve 'openai.beta.agents.sessions 'session-delete))))
