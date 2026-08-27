(ns openai.realtime-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [openai.realtime :as realtime])
  (:import (java.util.concurrent LinkedBlockingQueue TimeUnit)
           (com.openai.models.beta.realtime.sessions SessionCreateParams
                                                       SessionCreateParams$Modality)
           (com.openai.models.beta.realtime.transcriptionsessions TranscriptionSessionCreateParams)
           (com.openai.models.realtime RealtimeSessionCreateRequest)
           (com.openai.models.realtime.calls CallAcceptParams CallHangupParams
                                              CallReferParams CallRejectParams)
           (com.openai.models.realtime.clientsecrets ClientSecretCreateParams
                                                       ClientSecretCreateParams$ExpiresAfter
                                                       ClientSecretCreateResponse)))

(def mapper (json/object-mapper {:decode-key-fn true}))

(defn- json-map [s]
  (json/read-value s mapper))

(defn- fake-websocket [sent closed]
  (proxy [okhttp3.WebSocket] []
    (request [] nil)
    (queueSize [] 0)
    (send [value]
      (if (string? value)
        (swap! sent conj value)
        value)
      true)
    (close [code reason] (swap! closed conj [code reason]) true)
    (cancel [] (swap! closed conj :cancel) nil)))

(defn- scheduler-fixture []
  (let [tasks (atom [])]
    {:tasks tasks
     :schedule (fn [delay-ms task]
                 (let [entry (atom {:delay-ms delay-ms :task task :cancelled? false})]
                   (swap! tasks conj entry)
                   #(swap! entry assoc :cancelled? true)))}))

(defn- run-task! [entry]
  (when-not (:cancelled? @entry)
    ((:task @entry))))

(deftest reconnects-a-dropped-connection-with-preserved-callbacks
  (let [s (scheduler-fixture)
        listeners (atom [])
        sockets (atom [])
        opens (atom [])
        sent (atom [])
        closed (atom [])
        connection (realtime/connect
                    {:api-key "test"
                     :okhttp-client (okhttp3.OkHttpClient.)
                     :websocket-factory (fn [_ listener]
                                          (swap! listeners conj listener)
                                          (let [ws (fake-websocket sent closed)]
                                            (swap! sockets conj ws)
                                            ws))
                     :schedule-fn (:schedule s)
                     :auto-reconnect? true
                     :reconnect-base-delay-ms 10
                     :reconnect-jitter 0
                     :on-open (fn [ws response] (swap! opens conj [ws response]))})]
    (is (= 1 (count @listeners)))
    (when-let [listener (last @listeners)]
      (.onFailure listener (first @sockets) (RuntimeException. "drop") nil))
    (is (= 1 (count @listeners)))
    (is (= 10 (:delay-ms @(first @(:tasks s)))))
    (when-let [task (first @(:tasks s))]
      (run-task! task))
    (is (= 2 (count @listeners)))
    (.onOpen (last @listeners) (second @sockets) nil)
    (is (= 1 (count @opens)))
    (realtime/close! connection)))

(deftest reconnect-backoff-is-deterministic-and-bounded
  (let [s (scheduler-fixture)
        listeners (atom [])
        sockets (atom [])
        connection (realtime/connect
                    {:api-key "test"
                     :okhttp-client (okhttp3.OkHttpClient.)
                     :websocket-factory (fn [_ listener]
                                          (swap! listeners conj listener)
                                          (let [ws (fake-websocket (atom []) (atom []))]
                                            (swap! sockets conj ws)
                                            ws))
                     :schedule-fn (:schedule s)
                     :auto-reconnect? true
                     :reconnect-base-delay-ms 10
                     :reconnect-max-delay-ms 25
                     :reconnect-jitter 0
                     :reconnect-max-attempts 2})]
    (is (= 1 (count @listeners)))
    (when-let [listener (first @listeners)]
      (.onFailure listener (first @sockets) (RuntimeException. "drop") nil))
    (when-let [task (first @(:tasks s))]
      (run-task! task))
    (when-let [listener (second @listeners)]
      (.onFailure listener (second @sockets) (RuntimeException. "drop") nil))
    (when-let [task (second @(:tasks s))]
      (run-task! task))
    (when-let [listener (nth @listeners 2 nil)]
      (.onFailure listener (nth @sockets 2) (RuntimeException. "drop") nil))
    (is (= [10 20] (mapv #(-> % deref :delay-ms) @(:tasks s))))
    (realtime/close! connection)))

(deftest reconnects-an-unexpected-remote-close
  (let [s (scheduler-fixture)
        listeners (atom [])
        sockets (atom [])
        connection (realtime/connect
                    {:api-key "test"
                     :okhttp-client (okhttp3.OkHttpClient.)
                     :websocket-factory (fn [_ listener]
                                          (swap! listeners conj listener)
                                          (let [ws (fake-websocket (atom []) (atom []))]
                                            (swap! sockets conj ws)
                                            ws))
                     :schedule-fn (:schedule s)
                     :auto-reconnect? true
                     :reconnect-base-delay-ms 10
                     :reconnect-jitter 0})]
    (.onClosed (first @listeners) (first @sockets) 1006 "remote drop")
    (is (= [10] (mapv #(-> % deref :delay-ms) @(:tasks s))))
    (when-let [task (first @(:tasks s))]
      (run-task! task))
    (is (= 2 (count @listeners)))
    (realtime/close! connection)))

(deftest explicit-close-never-reconnects-after-remote-close-callback
  (let [s (scheduler-fixture)
        listeners (atom [])
        sockets (atom [])
        connection (realtime/connect
                    {:api-key "test"
                     :okhttp-client (okhttp3.OkHttpClient.)
                     :websocket-factory (fn [_ listener]
                                          (swap! listeners conj listener)
                                          (let [ws (fake-websocket (atom []) (atom []))]
                                            (swap! sockets conj ws)
                                            ws))
                     :schedule-fn (:schedule s)
                     :auto-reconnect? true})]
    (realtime/close! connection)
    (.onClosed (first @listeners) (first @sockets) 1000 "client closing")
    (is (empty? @(:tasks s)))))

(deftest idle-timeout-is-an-explicit-event
  (let [s (scheduler-fixture)
        listeners (atom [])
        sockets (atom [])
        events (atom [])
        clock (atom 0)
        connection (realtime/connect
                    {:api-key "test"
                     :okhttp-client (okhttp3.OkHttpClient.)
                     :websocket-factory (fn [_ listener]
                                          (swap! listeners conj listener)
                                          (let [ws (fake-websocket (atom []) (atom []))]
                                            (swap! sockets conj ws)
                                            ws))
                     :schedule-fn (:schedule s)
                     :heartbeat-interval-ms 10
                     :idle-timeout-ms 20
                     :now-fn #(long @clock)
                     :on-event #(swap! events conj %)})]
    (when-let [listener (first @listeners)]
      (.onOpen listener nil nil))
    (reset! clock 10)
    (.onMessage (first @listeners) (first @sockets)
                "{\"type\":\"response.done\",\"response\":{}}")
    (run-task! (first @(:tasks s)))
    (is (not-any? #(= :connection.idle-timeout (:type %)) @events))
    (reset! clock 31)
    (doseq [entry (rest @(:tasks s))] (run-task! entry))
    (is (some #(= :connection.idle-timeout (:type %)) @events))
    (realtime/close! connection)))

(deftest close-cancels-reliability-work
  (let [s (scheduler-fixture)
        listeners (atom [])
        connection (realtime/connect
                    {:api-key "test"
                     :okhttp-client (okhttp3.OkHttpClient.)
                     :websocket-factory (fn [_ listener]
                                          (swap! listeners conj listener)
                                          (fake-websocket (atom []) (atom [])))
                     :schedule-fn (:schedule s)
                     :auto-reconnect? true
                     :heartbeat-interval-ms 10
                     :idle-timeout-ms 20})]
    (when-let [listener (first @listeners)]
      (.onOpen listener nil nil))
    (realtime/close! connection)
    (is (every? :cancelled? (map deref @(:tasks s))))))

(deftest dispatch-blocks-until-a-bounded-queue-has-room
  (let [queue (LinkedBlockingQueue. 1)
        first-event {:type :response.audio.delta :delta "first"}
        second-event {:type :response.audio.delta :delta "second"}
        callbacks (atom [])]
    (.put queue first-event)
    (let [dispatch (future (#'realtime/dispatch! queue #(swap! callbacks conj %) second-event))]
      (is (= ::blocked (deref dispatch 100 ::blocked)))
      (is (= first-event (.poll queue 100 TimeUnit/MILLISECONDS)))
      (is (not= ::timed-out (deref dispatch 1000 ::timed-out)))
      (is (= second-event (.poll queue 100 TimeUnit/MILLISECONDS)))
      (is (= [second-event] @callbacks)))))

(deftest encodes-client-events-as-wire-json
  (testing "session configuration and nested enum names"
    (is (= {:type "session.update"
            :event_id "evt_1"
            :session {:output_modalities ["text" "audio"]
                      :audio {:input {:format {:type "audio/pcm" :rate 24000}}}}}
           (json-map
            (realtime/encode-client-event
             {:type :session.update
              :event-id "evt_1"
              :session {:output-modalities [:text :audio]
                        :audio {:input {:format {:type :audio/pcm :rate 24000}}}}})))))
  (testing "audio buffer events preserve base64 payloads"
    (is (= {:type "input_audio_buffer.append" :audio "AQID"}
           (json-map (realtime/encode-client-event
                      {:type :input-audio-buffer.append :audio "AQID"})))))
  (testing "conversation and response events"
    (doseq [event [{:type :input-audio-buffer.commit}
                   {:type :input-audio-buffer.clear}
                   {:type :conversation.item.create
                    :previous-item-id "item_0"
                    :item {:type :message :role :user
                           :content [{:type :input-text :text "hello"}]}}
                   {:type :response.create :response {:output-modalities [:text]}}
                   {:type :response.cancel :response-id "resp_1"}]]
      (is (= (str/replace (name (:type event)) "-" "_")
             (:type (json-map (realtime/encode-client-event event))))))))

(deftest rejects-client-events-without-a-type
  (is (= {:openai/error :missing-key :key :type}
         (try
           (realtime/encode-client-event {:audio "AQID"})
           nil
           (catch clojure.lang.ExceptionInfo e (ex-data e))))))

(deftest decodes-server-events-to-normalized-maps
  (doseq [[wire expected]
          [["{\"type\":\"response.text.delta\",\"event_id\":\"evt_1\",\"response_id\":\"r1\",\"delta\":\"Hi\"}"
            {:type :response.text.delta :event-id "evt_1" :response-id "r1" :delta "Hi"}]
           ["{\"type\":\"response.audio.delta\",\"delta\":\"AQID\",\"content_index\":0}"
            {:type :response.audio.delta :delta "AQID" :content-index 0}]
           ["{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"i1\",\"transcript\":\"hello\"}"
            {:type :conversation.item.input-audio-transcription.completed
             :item-id "i1" :transcript "hello"}]
           ["{\"type\":\"response.done\",\"response\":{\"id\":\"r1\",\"status\":\"completed\",\"output\":[]}}"
            {:type :response.done :response {:id "r1" :status :completed :output []}}]
           ["{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"code\":\"bad\",\"message\":\"nope\"}}"
            {:type :error :error {:type :invalid-request-error :code "bad" :message "nope"}}]]]
    (is (= expected (realtime/decode-server-event wire)))))

(deftest preserves-unknown-server-events
  (is (= {:type :future.event :new-field {:nested-value 1}}
         (realtime/decode-server-event
          "{\"type\":\"future.event\",\"new_field\":{\"nested_value\":1}}"))))

(deftest builds-stable-client-secret-params
  (let [^ClientSecretCreateParams p
        (#'realtime/->client-secret-params
         {:expires-after {:anchor :created-at :seconds 600}
          :session {:type :realtime :model "gpt-realtime" :instructions "Be brief"}})
        ^ClientSecretCreateParams$ExpiresAfter expiry (.get (.expiresAfter p))
        session-union (.get (.session p))
        ^RealtimeSessionCreateRequest session (.asRealtime session-union)]
    (is (= "created_at" (-> expiry .anchor .get .asString)))
    (is (= 600 (.get (.seconds expiry))))
    (is (= "gpt-realtime" (-> session .model .get .asString)))
    (is (= "Be brief" (.get (.instructions session))))))

(deftest builds-legacy-session-params
  (let [^SessionCreateParams p
        (#'realtime/->session-params
         {:model :gpt-realtime :voice :alloy :modalities [:audio :text]})]
    (is (= "gpt-realtime" (-> p .model .get .asString)))
    (is (= "alloy" (-> p .voice .get .asString)))
    (is (= ["audio" "text"]
           (mapv #(.asString ^SessionCreateParams$Modality %)
                 (.get (.modalities p)))))))

(deftest builds-transcription-session-params
  (let [^TranscriptionSessionCreateParams p
        (#'realtime/->transcription-session-params
         {:input-audio-format :pcm16
          :include ["item.input_audio_transcription.logprobs"]})]
    (is (= "pcm16" (-> p .inputAudioFormat .get .asString)))
    (is (= ["item.input_audio_transcription.logprobs"]
           (.get (.include p))))))

(deftest converts-client-secret-response
  (let [^ClientSecretCreateResponse response
        (-> (ClientSecretCreateResponse/builder)
            (.expiresAt 1234)
            (.value "ek_test")
            (.session (-> (com.openai.models.realtime.clientsecrets.RealtimeSessionCreateResponse/builder)
                          (.id "sess_1")
                          (.model "gpt-realtime")
                          (.build)))
            (.build))]
    (is (= {:value "ek_test" :expires-at 1234}
           (select-keys (#'realtime/client-secret-response->map response)
                        [:value :expires-at])))
    (is (= {:id "sess_1" :type :realtime :model "gpt-realtime"}
           (select-keys (:session (#'realtime/client-secret-response->map response))
                        [:id :type :model])))))

(deftest builds-translation-session-request-body
  (is (= {:session {:model "gpt-realtime-translate"
                    :audio {:output {:language "es"}}}
          :expires-after {:anchor "created_at" :seconds 300}}
         (#'realtime/translation-request-body
          {:session {:model "gpt-realtime-translate"
                     :audio {:output {:language "es"}}}
           :expires-after {:anchor :created-at :seconds 300}}))))

(deftest builds-sip-call-params
  (let [^CallAcceptParams accept (#'realtime/->accept-call-params
                                  "call_1" {:model "gpt-realtime"})
        ^CallHangupParams hangup (#'realtime/->hangup-call-params "call_1")
        ^CallReferParams refer (#'realtime/->refer-call-params
                                "call_1" "tel:+15551234567")
        ^CallRejectParams reject (#'realtime/->reject-call-params "call_1" 486)]
    (is (= "call_1" (.get (.callId accept))))
    (let [^RealtimeSessionCreateRequest session
          (.realtimeSessionCreateRequest accept)]
      (is (= "gpt-realtime" (-> session .model .get .asString))))
    (is (= "call_1" (.get (.callId hangup))))
    (is (= "tel:+15551234567" (.targetUri refer)))
    (is (= 486 (.get (.statusCode reject))))))
