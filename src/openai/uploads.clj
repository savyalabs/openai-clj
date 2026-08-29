(ns openai.uploads
  "Clojure wrapper for the OpenAI Uploads API."
  (:refer-clojure :exclude [complete])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.files FileObject FilePurpose)
           (com.openai.models.uploads Upload UploadCompleteParams UploadCreateParams)
           (com.openai.models.uploads.parts PartCreateParams UploadPart)
           (com.openai.services.blocking UploadService)
           (com.openai.services.blocking.uploads PartService)
           (java.io File InputStream)
           (java.nio.file Path)))

(set! *warn-on-reflection* true)

(defn- ->create-params ^UploadCreateParams
  [{:keys [filename bytes mime-type purpose]}]
  (doseq [[k v] [[:filename filename] [:bytes bytes] [:mime-type mime-type]
                  [:purpose purpose]]]
    (when (nil? v) (impl/missing-key! k)))
  (-> (UploadCreateParams/builder)
      (.filename ^String filename)
      (.bytes (long bytes))
      (.mimeType ^String mime-type)
      (.purpose (FilePurpose/of (impl/enum-name purpose)))
      (.build)))

(defn- file->map [^FileObject f]
  (cond-> {:id (.id f) :bytes (.bytes f) :created-at (.createdAt f)
           :filename (.filename f) :purpose (impl/->keyword (.asString (.purpose f)))
           :status (impl/->keyword (.asString (.status f)))}
    (.isPresent (.expiresAt f)) (assoc :expires-at (impl/opt-get (.expiresAt f)))
    (.isPresent (.statusDetails f)) (assoc :status-details (impl/opt-get (.statusDetails f)))))

(defn- upload->map [^Upload u]
  (cond-> {:id (.id u) :filename (.filename u) :bytes (.bytes u)
           :purpose (impl/->keyword (.purpose u))
           :status (impl/->keyword (.asString (.status u)))
           :created-at (.createdAt u) :expires-at (.expiresAt u)}
    (.isPresent (.file u)) (assoc :file (file->map (impl/opt-get (.file u))))))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^UploadService svc (.uploads client)]
      (upload->map (.create svc (->create-params req))))))

(defn- ->part-params ^PartCreateParams [^String upload-id data]
  (when-not data (impl/missing-key! :data))
  (let [b (PartCreateParams/builder)]
    (.uploadId b upload-id)
    (cond
      (bytes? data) (.data b ^bytes data)
      (instance? InputStream data) (.data b ^InputStream data)
      (instance? Path data) (.data b ^Path data)
      (string? data) (.data b (.toPath (File. ^String data)))
      :else (throw (ex-info (str "Unsupported :data type " (class data))
                            {:openai/error :unsupported-file-type :class (class data)})))
    (.build b)))

(defn add-part [^OpenAIClient client ^String upload-id {:keys [data]}]
  (impl/with-api-errors
    (let [^PartService svc (.parts (.uploads client))
          ^UploadPart p (.create svc (->part-params upload-id data))]
      {:id (.id p) :upload-id (.uploadId p) :created-at (.createdAt p)})))

(defn- ->complete-params ^UploadCompleteParams
  [^String upload-id {:keys [part-ids md5]}]
  (when-not part-ids (impl/missing-key! :part-ids))
  (let [b (UploadCompleteParams/builder)]
    (.uploadId b upload-id) (.partIds b ^java.util.List part-ids)
    (when md5 (.md5 b ^String md5))
    (.build b)))

(defn complete [^OpenAIClient client ^String upload-id req]
  (impl/with-api-errors
    (let [^UploadService svc (.uploads client)]
      (upload->map (.complete svc (->complete-params upload-id req))))))

(defn cancel [^OpenAIClient client ^String upload-id]
  (impl/with-api-errors
    (let [^UploadService svc (.uploads client)]
      (upload->map (.cancel svc upload-id)))))
