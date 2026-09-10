(ns openai.live
  "WebRTC Live session creation and lifecycle operations."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.live LiveCreateParams
                                   LiveCreateResponse
                                   MediaSessionConfig
                                   MediaSessionConfig$Builder
                                   MediaSessionConfig$Model)
           (com.openai.models.live LiveCreateParams$Transport)
           (com.openai.services.blocking LiveService)))

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
