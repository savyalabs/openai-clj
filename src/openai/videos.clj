(ns openai.videos
  "Clojure wrapper for the OpenAI Videos API."
  (:refer-clojure :exclude [list extend])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http HttpResponse)
           (com.openai.models.videos Video VideoCreateCharacterResponse VideoCreateError
                                      VideoCreateParams VideoDeleteResponse
                                      VideoDownloadContentParams$Variant VideoListPage
                                      VideoListParams VideoListParams$Order
                                      VideoSeconds VideoSize)
           (com.openai.services.blocking VideoService)))
(set! *warn-on-reflection* true)

(defn- set-create-reference! [b input]
  (cond (bytes? input) (.inputReference ^com.openai.models.videos.VideoCreateParams$Builder b ^bytes input)
        (instance? java.io.InputStream input) (.inputReference ^com.openai.models.videos.VideoCreateParams$Builder b ^java.io.InputStream input)
        (instance? java.nio.file.Path input) (.inputReference ^com.openai.models.videos.VideoCreateParams$Builder b ^java.nio.file.Path input)
        (string? input) (.inputReference ^com.openai.models.videos.VideoCreateParams$Builder b (.toPath (java.io.File. ^String input)))))
(defn- ->create-params ^VideoCreateParams [{:keys [prompt model size seconds input-reference]}]
  (when-not prompt (impl/missing-key! :prompt))
  (let [b (VideoCreateParams/builder)]
    (.prompt b ^String prompt)
    (when model (.model b ^String model))
    (when size (.size b (VideoSize/of (str size))))
    (when seconds (.seconds b (VideoSeconds/of (str seconds))))
    (when input-reference (set-create-reference! b input-reference)) (.build b)))
(defn- video-error->map [^VideoCreateError error]
  {:code (.code error) :message (.message error)})

(defn- video->map [^Video v]
  (cond-> {:id (.id v) :status (impl/->keyword (.asString (.status v)))
           :model (impl/->keyword (.asString (.model v))) :progress (.progress v) :created-at (.createdAt v)
           :size (impl/->keyword (.asString (.size v))) :seconds (impl/->keyword (.asString (.seconds v)))}
    (.isPresent (.completedAt v)) (assoc :completed-at (impl/opt-get (.completedAt v)))
    (.isPresent (.expiresAt v)) (assoc :expires-at (impl/opt-get (.expiresAt v)))
    (.isPresent (.error v)) (assoc :error (video-error->map (impl/opt-get (.error v))))
    (.isPresent (.prompt v)) (assoc :prompt (impl/opt-get (.prompt v)))))

(defn- delete-response->map [^VideoDeleteResponse response]
  {:id (.id response) :deleted (.deleted response)})

(defn- character->map [^VideoCreateCharacterResponse character]
  (cond-> {:created-at (.createdAt character)}
    (.isPresent (.id character)) (assoc :id (impl/opt-get (.id character)))
    (.isPresent (.name character)) (assoc :name (impl/opt-get (.name character)))))
(defn create [^OpenAIClient client req]
  (impl/with-api-errors (let [^VideoService svc (.videos client)]
                          (video->map (.create svc (->create-params req))))))
(defn retrieve [^OpenAIClient client ^String id]
  (impl/with-api-errors (let [^VideoService svc (.videos client)] (video->map (.retrieve svc id)))))
(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client {:keys [after limit order]}]
   (impl/with-api-errors
     (let [b (VideoListParams/builder) _ (when after (.after b ^String after))
           _ (when limit (.limit b (long limit)))
           _ (when order (.order b (VideoListParams$Order/of (name order))))
           ^VideoService svc (.videos client) ^VideoListPage page (.list svc (.build b))]
       (mapv video->map (impl/all-pages page))))))
(defn delete [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^VideoService svc (.videos client) ^VideoDeleteResponse d (.delete svc id)]
      (delete-response->map d))))
(defn remix [^OpenAIClient client ^String id {:keys [prompt]}]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.videos.VideoRemixParams/builder)
                (.videoId id) (.prompt ^String prompt) (.build)) ^VideoService svc (.videos client)]
      (video->map (.remix svc p)))))
(defn download-content
  (^bytes [^OpenAIClient client ^String id] (download-content client id {}))
  (^bytes [^OpenAIClient client ^String id {:keys [variant]}]
   (impl/with-api-errors
     (let [b (com.openai.models.videos.VideoDownloadContentParams/builder) _ (.videoId b id)
           _ (when variant (.variant b (VideoDownloadContentParams$Variant/of (name variant))))
           ^VideoService svc (.videos client)]
       (with-open [^HttpResponse r (.downloadContent svc (.build b))]
         (.readAllBytes (.body r)))))))

(defn- set-edit-video! [^com.openai.models.videos.VideoEditParams$Builder b video]
  (cond (bytes? video) (.video b ^bytes video)
        (instance? java.io.InputStream video) (.video b ^java.io.InputStream video)
        (instance? java.nio.file.Path video) (.video b ^java.nio.file.Path video)
        (string? video) (.video b (.toPath (java.io.File. ^String video)))))
(defn edit [^OpenAIClient client {:keys [video prompt]}]
  (impl/with-api-errors
    (let [b (com.openai.models.videos.VideoEditParams/builder) _ (set-edit-video! b video)
          _ (.prompt b ^String prompt) ^VideoService svc (.videos client)] (video->map (.edit svc (.build b))))))
(defn- set-extend-video! [^com.openai.models.videos.VideoExtendParams$Builder b video]
  (cond (bytes? video) (.video b ^bytes video)
        (instance? java.io.InputStream video) (.video b ^java.io.InputStream video)
        (instance? java.nio.file.Path video) (.video b ^java.nio.file.Path video)
        (string? video) (.video b (.toPath (java.io.File. ^String video)))))
(defn extend [^OpenAIClient client {:keys [video prompt seconds]}]
  (impl/with-api-errors
    (let [b (com.openai.models.videos.VideoExtendParams/builder) _ (set-extend-video! b video)
          _ (.prompt b ^String prompt) _ (when seconds (.seconds b (VideoSeconds/of (str seconds))))
          ^VideoService svc (.videos client)] (video->map (.extend svc (.build b))))))
(defn- set-character-video! [^com.openai.models.videos.VideoCreateCharacterParams$Builder b video]
  (cond (bytes? video) (.video b ^bytes video)
        (instance? java.io.InputStream video) (.video b ^java.io.InputStream video)
        (instance? java.nio.file.Path video) (.video b ^java.nio.file.Path video)
        (string? video) (.video b (.toPath (java.io.File. ^String video)))))
(defn create-character [^OpenAIClient client {:keys [name video]}]
  (impl/with-api-errors
    (let [b (com.openai.models.videos.VideoCreateCharacterParams/builder) _ (.name b ^String name)
          _ (set-character-video! b video) ^VideoService svc (.videos client)]
      (character->map (.createCharacter svc (.build b))))))
(defn get-character [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^VideoService svc (.videos client)] (character->map (.getCharacter svc id)))))
