(ns examples.batch
  "Submit, poll, and download a Responses batch."
  (:require [jsonista.core :as json]
            [openai.core :as openai])
  (:import (java.nio.charset StandardCharsets)))

(defn- env-long [name default]
  (Long/parseLong (or (System/getenv name) (str default))))

(defn- requests-jsonl []
  (str (json/write-value-as-string
        {:custom_id "clojure-example-1"
         :method "POST"
         :url "/v1/responses"
         :body {:model (or (System/getenv "OPENAI_BATCH_MODEL") "gpt-5.2")
                :input "Give one concise fact about Clojure."}})
       "\n"))

(defn run-batch
  "Upload one JSONL request, submit its batch, poll with exponential backoff,
  and return the downloaded output JSONL as a string.

  OPENAI_API_KEY is required. OPENAI_BATCH_MAX_POLLS and
  OPENAI_BATCH_INITIAL_DELAY_MS can tune the offline-friendly polling loop."
  []
  (let [client (openai/client)]
    (try
      (let [uploaded (openai/upload-file
                      client
                      {:file (.getBytes ^String (requests-jsonl) StandardCharsets/UTF_8)
                       :filename "openai-clj-example.jsonl"
                       :purpose :batch})
            submitted (openai/create-batch
                       client
                       {:input-file-id (:id uploaded)
                        :endpoint "/v1/responses"
                        :completion-window "24h"})
            terminal #{:completed :failed :expired :cancelled}
            max-polls (env-long "OPENAI_BATCH_MAX_POLLS" 12)
            initial-delay (env-long "OPENAI_BATCH_INITIAL_DELAY_MS" 1000)]
        (loop [batch submitted poll-count 0 delay-ms initial-delay]
          (println (:status batch) "poll" poll-count)
          (cond
            (= :completed (:status batch))
            (String. (openai/file-content client (:output-file-id batch))
                     StandardCharsets/UTF_8)

            (contains? terminal (:status batch))
            (throw (ex-info "Batch did not complete successfully"
                            {:openai/error :batch-failed :batch batch}))

            (>= poll-count max-polls)
            (throw (ex-info "Batch polling limit reached"
                            {:openai/error :timeout :batch batch}))

            :else
            (do
              (Thread/sleep delay-ms)
              (recur (openai/get-batch client (:id batch))
                     (inc poll-count)
                     (min (* 2 delay-ms) 30000))))))
      (finally
        (.close client)))))

(comment
  ;; OPENAI_API_KEY is read from the environment. This performs live API calls.
  (def output-jsonl (run-batch))
  (println output-jsonl))
