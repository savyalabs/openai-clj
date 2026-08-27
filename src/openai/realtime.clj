(ns openai.realtime
  "Realtime WebSocket sessions and REST helpers.

  The WebSocket codec is pure. `connect` adds an OkHttp transport. It provides
  callbacks and a blocking queue through `take!` and `poll!`."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [jsonista.core :as json]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.realtime.sessions SessionCreateParams
                                                       SessionCreateParams$Builder
                                                       SessionCreateParams$Modality
                                                       SessionCreateParams$Model)
           (com.openai.models.beta.realtime.transcriptionsessions TranscriptionSessionCreateParams
                                                                    TranscriptionSessionCreateParams$Builder
                                                                    TranscriptionSessionCreateParams$InputAudioFormat
                                                                    TranscriptionSessionCreateParams$Modality)
           (com.openai.models.realtime RealtimeSessionCreateRequest
                                       RealtimeSessionCreateRequest$Builder
                                       RealtimeTranscriptionSessionCreateRequest
                                       RealtimeTranscriptionSessionCreateRequest$Builder)
           (com.openai.models.realtime.calls CallAcceptParams
                                              CallHangupParams
                                              CallReferParams
                                              CallRejectParams)
           (com.openai.models.realtime.clientsecrets ClientSecretCreateParams
                                                      ClientSecretCreateParams$Builder
                                                      ClientSecretCreateParams$ExpiresAfter
                                                      ClientSecretCreateParams$ExpiresAfter$Anchor
                                                      ClientSecretCreateResponse)
           (java.net URLEncoder)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent Executors LinkedBlockingQueue ScheduledExecutorService
                                  TimeUnit)
           (okhttp3 MediaType OkHttpClient Request Request$Builder RequestBody
                    Response ResponseBody WebSocket WebSocketListener)))

(set! *warn-on-reflection* true)

(def ^:private wire-mapper (json/object-mapper {:decode-key-fn true}))

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

(defn encode-client-event
  "Encode a Clojure client-event map as Realtime wire JSON."
  ^String [event]
  (when-not (:type event) (impl/missing-key! :type))
  (json/write-value-as-string (->wire event)))

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

(defn decode-server-event
  "Decode Realtime wire JSON to a normalized Clojure map."
  [^String wire-json]
  (normalize-map (json/read-value wire-json wire-mapper)))

(defn- json-value [x]
  (JsonValue/from (->wire x)))

(defn- put-realtime-properties!
  [^RealtimeSessionCreateRequest$Builder builder m]
  (doseq [[k v] m]
    (.putAdditionalProperty builder (wire-name k) (json-value v)))
  builder)

(defn- put-transcription-properties!
  [^RealtimeTranscriptionSessionCreateRequest$Builder builder m]
  (doseq [[k v] m]
    (.putAdditionalProperty builder (wire-name k) (json-value v)))
  builder)

(defn- ->realtime-session ^RealtimeSessionCreateRequest [session]
  (let [^RealtimeSessionCreateRequest$Builder b (RealtimeSessionCreateRequest/builder)
        {:keys [model instructions]} session]
    (when model (.model b ^String (wire-name model)))
    (when instructions (.instructions b ^String instructions))
    (put-realtime-properties! b (dissoc session :type :model :instructions))
    (.build b)))

(defn- ->transcription-session ^RealtimeTranscriptionSessionCreateRequest [session]
  (let [^RealtimeTranscriptionSessionCreateRequest$Builder b
        (RealtimeTranscriptionSessionCreateRequest/builder)]
    (put-transcription-properties! b (dissoc session :type))
    (.build b)))

(defn- ->expires-after ^ClientSecretCreateParams$ExpiresAfter
  [{:keys [anchor seconds]}]
  (when-not anchor (impl/missing-key! :anchor))
  (when-not seconds (impl/missing-key! :seconds))
  (-> (ClientSecretCreateParams$ExpiresAfter/builder)
      (.anchor (ClientSecretCreateParams$ExpiresAfter$Anchor/of (wire-name anchor)))
      (.seconds (long seconds))
      (.build)))

(defn- ->client-secret-params ^ClientSecretCreateParams
  [{:keys [expires-after session]}]
  (let [^ClientSecretCreateParams$Builder b (ClientSecretCreateParams/builder)
        session (or session {:type :realtime})]
    (when expires-after (.expiresAfter b (->expires-after expires-after)))
    (case (:type session :realtime)
      :realtime (.session b (->realtime-session session))
      :transcription (.session b (->transcription-session session))
      (throw (ex-info "Unsupported Realtime client-secret session type"
                      {:openai/error :unsupported-session-type
                       :type (:type session)})))
    (.build b)))

(defn- ->session-params ^SessionCreateParams [req]
  (let [^SessionCreateParams$Builder b (SessionCreateParams/builder)
        {:keys [model voice modalities]} req]
    (when model (.model b (SessionCreateParams$Model/of (name model))))
    (when voice (.voice b ^String (name voice)))
    (when modalities
      (let [^java.util.List xs
            (mapv #(SessionCreateParams$Modality/of (wire-name %)) modalities)]
        (.modalities b xs)))
    (doseq [[k v] (dissoc req :model :voice :modalities)]
      (.putAdditionalBodyProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- ->transcription-session-params ^TranscriptionSessionCreateParams [req]
  (let [^TranscriptionSessionCreateParams$Builder b
        (TranscriptionSessionCreateParams/builder)
        {:keys [input-audio-format include modalities]} req]
    (when input-audio-format
      (.inputAudioFormat b (TranscriptionSessionCreateParams$InputAudioFormat/of
                            (wire-name input-audio-format))))
    (when include (.include b ^java.util.List include))
    (when modalities
      (let [^java.util.List xs
            (mapv #(TranscriptionSessionCreateParams$Modality/of (wire-name %))
                  modalities)]
        (.modalities b xs)))
    (doseq [[k v] (dissoc req :input-audio-format :include :modalities)]
      (.putAdditionalBodyProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- sdk-object->map [x]
  (normalize-map
   (json/read-value (json/write-value-as-string x) wire-mapper)))

(defn- client-secret-response->map [^ClientSecretCreateResponse response]
  (let [session (.session response)]
    {:value (.value response)
     :expires-at (.expiresAt response)
     :session (cond
                (.isRealtime session)
                (let [x (.asRealtime session)]
                  (assoc (sdk-object->map x) :type :realtime))
                (.isTranscription session)
                (assoc (sdk-object->map (.asTranscription session))
                       :type :transcription))}))

(defn create-client-secret
  "Create a short-lived Realtime or transcription client secret."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (-> client .realtime .clientSecrets
        (.create (->client-secret-params req))
        client-secret-response->map)))

(defn create-session
  "Create a legacy beta Realtime session and return a normalized map."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (sdk-object->map
     (-> client .beta .realtime .sessions (.create (->session-params req))))))

(defn create-transcription-session
  "Create a legacy beta Realtime transcription session."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (sdk-object->map
     (-> client .beta .realtime .transcriptionSessions
         (.create (->transcription-session-params req))))))

(defn- translation-request-body [req]
  (letfn [(convert [x]
            (cond
              (keyword? x) (wire-name x)
              (map? x) (into {} (map (fn [[k v]] [k (convert v)])) x)
              (sequential? x) (mapv convert x)
              :else x))]
    (convert req)))

(defn- post-json
  [^OkHttpClient http ^String url ^String api-key body]
  (let [request (-> (Request$Builder.)
                    (.url url)
                    (.header "Authorization" (str "Bearer " api-key))
                    (.post (RequestBody/create
                            (json/write-value-as-string (->wire body))
                            (MediaType/get "application/json")))
                    (.build))]
    (with-open [^Response response (.execute (.newCall http request))]
      (let [^ResponseBody response-body (.body response)
            text (if response-body (.string response-body) "")
            parsed (when-not (str/blank? text)
                     (decode-server-event text))]
        (if (.isSuccessful response)
          parsed
          (throw (ex-info (str "OpenAI API error: HTTP " (.code response))
                          {:openai/error :api-error
                           :status (.code response)
                           :body parsed})))))))

(defn create-translation-session
  "Create a short-lived Realtime Translation client secret.

  Take transport config because openai-java 4.42.0 has translation models but
  does not expose the translation REST service."
  [{:keys [api-key base-url okhttp-client]} req]
  (when-not api-key (impl/missing-key! :api-key))
  (let [owned? (nil? okhttp-client)
        ^OkHttpClient http (or okhttp-client (OkHttpClient.))]
    (try
      (post-json http
                 (str (or base-url "https://api.openai.com/v1")
                      "/realtime/translations/client_secrets")
                 api-key
                 (translation-request-body req))
      (finally
        (when owned?
          (-> http .dispatcher .executorService .shutdown)
          (-> http .connectionPool .evictAll))))))

(defn- ->accept-call-params ^CallAcceptParams [call-id session]
  (when-not call-id (impl/missing-key! :call-id))
  (-> (CallAcceptParams/builder)
      (.callId ^String call-id)
      (.realtimeSessionCreateRequest (->realtime-session session))
      (.build)))

(defn- ->hangup-call-params ^CallHangupParams [call-id]
  (when-not call-id (impl/missing-key! :call-id))
  (-> (CallHangupParams/builder)
      (.callId ^String call-id)
      (.build)))

(defn- ->refer-call-params ^CallReferParams [call-id target-uri]
  (when-not call-id (impl/missing-key! :call-id))
  (when-not target-uri (impl/missing-key! :target-uri))
  (-> (CallReferParams/builder)
      (.callId ^String call-id)
      (.targetUri ^String target-uri)
      (.build)))

(defn- ->reject-call-params ^CallRejectParams [call-id status-code]
  (when-not call-id (impl/missing-key! :call-id))
  (let [b (-> (CallRejectParams/builder) (.callId ^String call-id))]
    (when status-code (.statusCode b (long status-code)))
    (.build b)))

(defn accept-call
  "Accept a SIP call using a Realtime session configuration."
  [^OpenAIClient client call-id session]
  (impl/with-api-errors
    (-> client .realtime .calls
        (.accept (->accept-call-params call-id session))))
  nil)

(defn hangup-call [^OpenAIClient client call-id]
  (impl/with-api-errors
    (-> client .realtime .calls
        (.hangup (->hangup-call-params call-id))))
  nil)

(defn refer-call [^OpenAIClient client call-id target-uri]
  (impl/with-api-errors
    (-> client .realtime .calls
        (.refer (->refer-call-params call-id target-uri))))
  nil)

(defn reject-call
  ([client call-id] (reject-call client call-id nil))
  ([^OpenAIClient client call-id status-code]
   (impl/with-api-errors
     (-> client .realtime .calls
         (.reject (->reject-call-params call-id status-code))))
   nil))

(defrecord RealtimeConnection [socket queue http owned-http? work closed? scheduler]
  java.io.Closeable
  (close [_]
    (when (compare-and-set! closed? false true)
      (doseq [cancel @work] (cancel))
      (when-let [^WebSocket ws @socket]
        (.close ws 1000 "client closing")))
    (when scheduler (.shutdownNow ^ScheduledExecutorService scheduler))
    (when owned-http?
      (-> ^OkHttpClient http .dispatcher .executorService .shutdown)
      (-> ^OkHttpClient http .connectionPool .evictAll))))

(defn- dispatch! [^LinkedBlockingQueue queue on-event event]
  (.put queue event)
  (when on-event (on-event event)))

(defn- ws-url [{:keys [url base-url model mode]}]
  (or url
      (let [root (or base-url "wss://api.openai.com/v1")
            endpoint (if (= mode :translation)
                       "/realtime/translations"
                       "/realtime")]
        (str root endpoint
             (when model
               (str "?model="
                    (URLEncoder/encode ^String model StandardCharsets/UTF_8)))))))

(defn connect
  "Open a Realtime WebSocket and return a RealtimeConnection. Bounded queues
  apply backpressure: dispatch blocks until consumers make room, never dropping
  events silently."
  [{:keys [api-key client-secret okhttp-client queue-capacity
           on-event on-open on-close on-error
           auto-reconnect? reconnect-base-delay-ms reconnect-max-delay-ms
           reconnect-jitter reconnect-max-attempts reconnect-max-duration-ms random-fn
           heartbeat-interval-ms idle-timeout-ms now-fn schedule-fn
           websocket-factory]
    :as opts}]
  (let [token (or client-secret api-key)]
    (when-not token (impl/missing-key! :api-key))
    (let [owned? (nil? okhttp-client)
          ^OkHttpClient http (or okhttp-client
                                 (if heartbeat-interval-ms
                                   (-> (OkHttpClient.) .newBuilder
                                       (.pingInterval (long heartbeat-interval-ms)
                                                      TimeUnit/MILLISECONDS)
                                       (.build))
                                   (OkHttpClient.)))
          queue (if queue-capacity
                  (LinkedBlockingQueue. (int queue-capacity))
                  (LinkedBlockingQueue.))
          socket (atom nil)
          ^Request$Builder request-builder (Request$Builder.)
          request (-> request-builder
                      (.url ^String (ws-url opts))
                      (.header "Authorization" ^String (str "Bearer " token))
                      (.build))
          closed? (atom false)
          work (atom [])
          now (or now-fn #(System/currentTimeMillis))
          random (or random-fn rand)
          scheduler (when (and (nil? schedule-fn)
                               (or auto-reconnect? heartbeat-interval-ms idle-timeout-ms))
                     (Executors/newSingleThreadScheduledExecutor))
          schedule (or schedule-fn
                       (fn [delay-ms task]
                         (let [future (.schedule ^ScheduledExecutorService scheduler
                                                  ^Runnable task (long delay-ms)
                                                  TimeUnit/MILLISECONDS)]
                           #(.cancel future false))))
          activity (atom (now))
          idle-reported? (atom false)
          reconnect-attempt (atom 0)
          reconnect-start (atom nil)
          retry-scheduled? (atom false)
          listener-ref (atom nil)
          factory (or websocket-factory
                      (fn [^Request request ^WebSocketListener listener]
                        (.newWebSocket http request listener)))
          add-work! (fn [cancel] (swap! work conj cancel) cancel)
          schedule-once! (fn [delay-ms task]
                           (add-work! (schedule delay-ms
                                                 (fn []
                                                   (when-not @closed?
                                                     (task))))))
          reconnect! (fn []
                       (let [attempt (swap! reconnect-attempt inc)
                             started (or @reconnect-start (reset! reconnect-start (now)))
                             within-duration? (or (nil? reconnect-max-duration-ms)
                                                 (< (- (now) started)
                                                    reconnect-max-duration-ms))]
                         (when (and (or (nil? reconnect-max-attempts)
                                        (<= attempt reconnect-max-attempts))
                                    within-duration?)
                           (let [base (long (or reconnect-base-delay-ms 1000))
                                 ceiling (long (or reconnect-max-delay-ms 30000))
                                 exponential (min ceiling (* base (long (Math/pow 2 (dec attempt)))))
                                 jitter (double (or reconnect-jitter 0))
                                 delay (long (max 0 (* exponential
                                                     (+ 1 (* jitter (- (* 2 (random) 1)))))))]
                             (reset! retry-scheduled? true)
                             (schedule-once! delay
                                              #(do
                                                 (reset! retry-scheduled? false)
                                                 (reset! socket (factory request @listener-ref))))))))
          reconnect-on-drop! (fn [^WebSocket ws]
                               (when (and auto-reconnect? (not @closed?) (= ws @socket)
                                          (compare-and-set! retry-scheduled? false true))
                                 (reconnect!)))
          idle-check! (fn idle-check! []
                        (when (and idle-timeout-ms
                                   (>= (- (now) @activity) idle-timeout-ms)
                                   (compare-and-set! idle-reported? false true))
                          (dispatch! queue on-event {:type :connection.idle-timeout}))
                        (when (and (or heartbeat-interval-ms idle-timeout-ms)
                                   (not @closed?))
                          (schedule-once! (long (or heartbeat-interval-ms idle-timeout-ms))
                                          idle-check!)))
          listener
          (proxy [WebSocketListener] []
            (onOpen [^WebSocket ws ^Response response]
              (reset! activity (now))
              (reset! idle-reported? false)
              (reset! reconnect-attempt 0)
              (reset! reconnect-start nil)
              (reset! retry-scheduled? false)
              (when idle-timeout-ms (schedule-once! (long idle-timeout-ms) idle-check!))
              (when on-open (on-open ws response)))
            (onMessage [^WebSocket _ ^String text]
              (reset! activity (now))
              (reset! idle-reported? false)
              (try
                (dispatch! queue on-event (decode-server-event text))
                (catch Throwable e
                  (when on-error (on-error e)))))
            (onClosing [^WebSocket ws code reason]
              (.close ws code reason))
            (onClosed [^WebSocket ws code reason]
              (when on-close (on-close {:code code :reason reason}))
              (reconnect-on-drop! ws))
            (onFailure [^WebSocket ws ^Throwable error ^Response response]
              (dispatch! queue on-event
                         {:type :connection.error
                          :error error
                          :status (when response (.code response))})
              (when on-error (on-error error))
              (reconnect-on-drop! ws)))]
      (reset! listener-ref listener)
      (reset! socket (factory request listener))
      (->RealtimeConnection socket queue http owned? work closed? scheduler))))

(defn send!
  "Encode and send a client event."
  [^RealtimeConnection connection event]
  (.send ^WebSocket @(:socket connection) (encode-client-event event)))

(defn take!
  "Block until the next normalized server event is ready."
  [^RealtimeConnection connection]
  (.take ^LinkedBlockingQueue (:queue connection)))

(defn poll!
  "Return the next event immediately, or wait up to timeout-ms."
  ([^RealtimeConnection connection]
   (.poll ^LinkedBlockingQueue (:queue connection)))
  ([^RealtimeConnection connection timeout-ms]
   (.poll ^LinkedBlockingQueue (:queue connection)
          (long timeout-ms) TimeUnit/MILLISECONDS)))

(defn close!
  "Close a Realtime connection."
  [^RealtimeConnection connection]
  (.close connection))
