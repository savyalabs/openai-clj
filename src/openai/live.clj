(ns openai.live
  "WebRTC Live session creation and lifecycle operations."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.core.http HttpResponse)
           (com.openai.models.live LiveCreateParams
                                   LiveCreateResponse
                                   MediaSessionConfig
                                   MediaSessionConfig$Builder
                                   MediaSessionConfig$Model
                                   MediaSessionForkConfig
                                   MediaSessionForkConfig$Builder)
           (com.openai.models.live LiveCreateParams$Transport)
           (com.openai.models.live.sessions SessionAcceptParams
                                             SessionAcceptParams$Session
                                             SessionAcceptParams$Session$Builder
                                             SessionAcceptParams$Session$Model
                                             SessionHangupParams
                                             SessionReferParams
                                             SessionRejectParams
                                             SessionForkParams
                                             SessionForkParams$Transport
                                             SessionForkResponse
                                             SessionDownloadRecordingParams)
           (com.openai.services.blocking LiveService)
           (com.openai.services.blocking.live SessionService)))

(set! *warn-on-reflection* true)

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

(defn- json-value [x]
  (JsonValue/from (->wire x)))

(defn- put-media-session-properties!
  [^MediaSessionConfig$Builder builder m]
  (doseq [[k v] m]
    (.putAdditionalProperty builder (wire-name k) (json-value v)))
  builder)

(defn- ->media-session ^MediaSessionConfig [session]
  (when-not session (impl/missing-key! :session))
  (let [{:keys [model]} session]
    (when-not model (impl/missing-key! :model))
    (let [^MediaSessionConfig$Builder b (MediaSessionConfig/builder)]
      (.model b (MediaSessionConfig$Model/of (if (keyword? model)
                                               (name model)
                                               model)))
      (put-media-session-properties! b (dissoc session :model))
      (.build b))))

(defn- ->live-transport ^LiveCreateParams$Transport [transport]
  (when-not transport (impl/missing-key! :transport))
  (let [{:keys [sdp]} transport]
    (when-not sdp (impl/missing-key! :sdp))
    (-> (LiveCreateParams$Transport/builder)
        (.sdp ^String sdp)
        (.build))))

(defn- ->live-create-params ^LiveCreateParams [{:keys [session transport]}]
  (-> (LiveCreateParams/builder)
      (.session (->media-session session))
      (.transport (->live-transport transport))
      (.build)))

(defn- live-response->map [^LiveCreateResponse response]
  {:session {:id (-> response .session .id)}
   :transport {:sdp (-> response .transport .sdp)}})

(defn live-create
  "Create a WebRTC Live session from a session config and SDP offer."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^LiveService service (.live client)]
      (live-response->map (.create service (->live-create-params req))))))

(defn- ->accept-session ^SessionAcceptParams$Session [session]
  (when-not session (impl/missing-key! :session))
  (let [{:keys [model]} session]
    (when-not model (impl/missing-key! :model))
    (let [^SessionAcceptParams$Session$Builder b
          (SessionAcceptParams$Session/builder)]
      (.model b (SessionAcceptParams$Session$Model/of
                 (if (keyword? model) (name model) model)))
      (doseq [[k v] (dissoc session :model)]
        (.putAdditionalProperty b (wire-name k) (json-value v)))
      (.build b))))

(defn- ->session-accept-params ^SessionAcceptParams [session-id session]
  (when-not session-id (impl/missing-key! :session-id))
  (-> (SessionAcceptParams/builder)
      (.sessionId ^String session-id)
      (.session (->accept-session session))
      (.build)))

(defn session-accept
  "Accept an incoming SIP Live session with startup configuration."
  [^OpenAIClient client session-id session]
  (impl/with-api-errors
    (let [^LiveService live (.live client)
          ^SessionService sessions (.sessions live)]
      (.accept sessions (->session-accept-params session-id session))))
  nil)

(defn- ->session-hangup-params ^SessionHangupParams [session-id]
  (when-not session-id (impl/missing-key! :session-id))
  (-> (SessionHangupParams/builder)
      (.sessionId ^String session-id)
      (.build)))

(defn session-hangup
  "Hang up a Live session."
  [^OpenAIClient client session-id]
  (impl/with-api-errors
    (let [^LiveService live (.live client)
          ^SessionService sessions (.sessions live)]
      (.hangup sessions (->session-hangup-params session-id))))
  nil)

(defn- ->session-refer-params ^SessionReferParams [session-id target-uri]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not target-uri (impl/missing-key! :target-uri))
  (-> (SessionReferParams/builder)
      (.sessionId ^String session-id)
      (.targetUri ^String target-uri)
      (.build)))

(defn session-refer
  "Refer a Live session to another SIP URI."
  [^OpenAIClient client session-id target-uri]
  (impl/with-api-errors
    (let [^LiveService live (.live client)
          ^SessionService sessions (.sessions live)]
      (.refer sessions (->session-refer-params session-id target-uri))))
  nil)

(defn- ->session-reject-params ^SessionRejectParams [session-id status-code]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not status-code (impl/missing-key! :status-code))
  (-> (SessionRejectParams/builder)
      (.sessionId ^String session-id)
      (.statusCode (long status-code))
      (.build)))

(defn session-reject
  "Reject an incoming Live session with a SIP status code."
  [^OpenAIClient client session-id status-code]
  (impl/with-api-errors
    (let [^LiveService live (.live client)
          ^SessionService sessions (.sessions live)]
      (.reject sessions (->session-reject-params session-id status-code))))
  nil)

(defn- ->fork-session ^MediaSessionForkConfig [session]
  (let [^MediaSessionForkConfig$Builder b (MediaSessionForkConfig/builder)]
    (doseq [[k v] session]
      (.putAdditionalProperty b (wire-name k) (json-value v)))
    (.build b)))

(defn- ->fork-transport ^SessionForkParams$Transport [transport]
  (when-not transport (impl/missing-key! :transport))
  (let [{:keys [sdp]} transport]
    (when-not sdp (impl/missing-key! :sdp))
    (-> (SessionForkParams$Transport/builder)
        (.sdp ^String sdp)
        (.build))))

(defn- ->session-fork-params ^SessionForkParams
  [session-id {:keys [transport session]}]
  (when-not session-id (impl/missing-key! :session-id))
  (let [b (-> (SessionForkParams/builder)
              (.sessionId ^String session-id)
              (.transport (->fork-transport transport)))]
    (when session (.session b (->fork-session session)))
    (.build b)))

(defn- fork-response->map [^SessionForkResponse response]
  {:session {:id (-> response .session .id)}
   :transport {:sdp (-> response .transport .sdp)}})

(defn session-fork
  "Fork a stored Live session onto a new WebRTC connection."
  [^OpenAIClient client session-id req]
  (impl/with-api-errors
    (let [^LiveService live (.live client)
          ^SessionService sessions (.sessions live)]
      (fork-response->map
       (.fork sessions (->session-fork-params session-id req))))))

(defn- ->session-download-recording-params ^SessionDownloadRecordingParams
  [session-id]
  (when-not session-id (impl/missing-key! :session-id))
  (-> (SessionDownloadRecordingParams/builder)
      (.sessionId ^String session-id)
      (.build)))

(defn session-download-recording
  "Download a stored Live session recording as a byte array."
  ^bytes [^OpenAIClient client session-id]
  (impl/with-api-errors
    (let [^LiveService live (.live client)
          ^SessionService sessions (.sessions live)]
      (with-open [^HttpResponse response
                  (.downloadRecording
                   sessions
                   (->session-download-recording-params session-id))]
        (.readAllBytes (.body response))))))
