(ns examples.tool-calling
  "A Responses function-tool loop with local tool execution."
  (:require [openai.core :as openai]))

(def weather-tool
  {:type :function
   :name "get_weather"
   :description "Return the current weather for a city."
   :strict true
   :parameters {:type "object"
                :properties {:location {:type "string"}}
                :required ["location"]}})

(defn- local-weather [{:keys [location]}]
  ;; Replace this deterministic stand-in with the application's local service.
  {:location location :temperature-f 72 :conditions "sunny"})

(defn run-tool-loop
  "Ask a question, execute each returned function call locally, and request
  the final answer. OPENAI_API_KEY and optionally OPENAI_MODEL configure it."
  []
  (let [client (openai/client)]
    (try
      (let [first-response
            (openai/create-response
             client
             {:model (or (System/getenv "OPENAI_MODEL") "gpt-5.2")
              :input (or (System/getenv "OPENAI_TOOL_PROMPT")
                         "What is the weather in Denver?")
              :tools [weather-tool]
              :tool-choice :auto})
            calls (filter #(= :function-call (:type %)) (:output first-response))
            outputs (mapv (fn [call]
                            {:type :function-call-output
                             :call-id (:call-id call)
                             :output (local-weather (:arguments call))})
                          calls)]
        (if (seq outputs)
          (openai/create-response
           client
           {:model (:model first-response)
            :previous-response-id (:id first-response)
            :input outputs})
          first-response))
      (finally
        (.close client)))))

(comment
  ;; OPENAI_API_KEY is read by (openai/client); no key is stored in this file.
  (def final-response (run-tool-loop))
  (:text final-response))
