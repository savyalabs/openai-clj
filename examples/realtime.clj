(ns examples.realtime
  "A text-only Realtime conversation over the public WebSocket API."
  (:require [openai.realtime :as realtime]))

(defn- env [name default]
  (or (System/getenv name) default))

(defn run-session
  "Connect, send one text turn, print server events, and close the session.

  Required environment variable: OPENAI_API_KEY (or
  OPENAI_REALTIME_CLIENT_SECRET for a short-lived client secret). Optional:
  OPENAI_REALTIME_MODEL, OPENAI_REALTIME_INSTRUCTIONS, and
  OPENAI_REALTIME_PROMPT."
  []
  (let [connection (realtime/connect
                    {:api-key (System/getenv "OPENAI_API_KEY")
                     :client-secret (System/getenv "OPENAI_REALTIME_CLIENT_SECRET")
                     :model (env "OPENAI_REALTIME_MODEL" "gpt-realtime")})]
    (try
      (realtime/send!
       connection
       {:type :session.update
        :session {:type :realtime
                  :instructions (env "OPENAI_REALTIME_INSTRUCTIONS"
                                     "Answer briefly and clearly.")
                  :output-modalities [:text]}})
      (realtime/send!
       connection
       {:type :conversation.item.create
        :item {:type :message
               :role :user
               :content [{:type :input_text
                          :text (env "OPENAI_REALTIME_PROMPT"
                                     "What is one useful fact about Clojure?")} ]}})
      (realtime/send! connection {:type :response.create})
      (loop [events []]
        (if-let [event (realtime/poll! connection 30000)]
          (do
            (println event)
            (if (contains? #{:response.done :error :connection.error} (:type event))
              (conj events event)
              (recur (conj events event))))
          (throw (ex-info "Timed out waiting for a Realtime response"
                          {:openai/error :timeout
                           :timeout-ms 30000}))))
      (finally
        (realtime/close! connection)))))

(comment
  ;; Set OPENAI_API_KEY in the shell before evaluating this block.
  ;; A client secret may be used instead via OPENAI_REALTIME_CLIENT_SECRET.
  (run-session))
