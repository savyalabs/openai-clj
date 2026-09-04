(ns openai.videos-test
  (:require [clojure.test :refer [deftest is]]
            [openai.videos :as videos])
  (:import (com.openai.core JsonValue)
           (com.openai.models.videos Video Video$Builder Video$Status VideoCreateCharacterResponse
                                     VideoCreateError VideoDeleteResponse
                                     VideoCreateParams VideoCreateParams$InputReference
                                     VideoModel VideoSeconds VideoSize)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o) (.get ^java.util.Optional o)))

(deftest translates-video-create
  (let [^VideoCreateParams p (#'videos/->create-params
                              {:prompt "A sunrise" :model "sora-2"
                               :size "1280x720" :seconds 8
                               :input-reference (.getBytes "png" "UTF-8")})]
    (is (= "A sunrise" (.prompt p)))
    (is (= "sora-2" (.asString ^VideoModel (opt (.model p)))))
    (is (= "1280x720" (.asString ^VideoSize (opt (.size p)))))
    (is (= "8" (.asString ^VideoSeconds (opt (.seconds p)))))
    (is (= [112 110 103]
           (vec (.readAllBytes
                 (.asStream ^VideoCreateParams$InputReference
                            (opt (.inputReference p)))))))))

(deftest converts-video-response-with-present-only-optionals
  (let [^java.util.Optional absent (java.util.Optional/empty)
        error (-> (VideoCreateError/builder) (.code "render_failed")
                  (.message "Could not render") (.build))
        ^Video$Builder base (doto (Video/builder)
                             (.id "video_1") (.createdAt 10) (.model "sora-2")
                             (.object_ (JsonValue/from "video")) (.progress 25)
                             (.seconds (VideoSeconds/of "8"))
                             (.size (VideoSize/of "1280x720"))
                             (.status (Video$Status/of "queued"))
                             (.completedAt absent) (.error absent)
                             (.expiresAt absent) (.prompt absent)
                             (.remixedFromVideoId absent))]
    (is (= {:id "video_1" :status :queued :model :sora-2 :progress 25
            :created-at 10 :size :1280x720 :seconds :8}
           (#'videos/video->map (.build base))))
    (let [full (#'videos/video->map
                (.build (doto base (.completedAt 20) (.expiresAt 30) (.prompt "A sunrise")
                          (.error error))))]
      (is (= {:id "video_1" :status :queued :model :sora-2 :progress 25
              :created-at 10 :size :1280x720 :seconds :8
              :completed-at 20 :expires-at 30 :prompt "A sunrise"}
             (dissoc full :error)))
      (is (= {:code "render_failed" :message "Could not render"}
             (:error full))))))

(deftest converts-character-with-present-only-optionals
  (let [convert (some-> (ns-resolve 'openai.videos 'character->map) deref)
        minimal (-> (VideoCreateCharacterResponse/builder)
                    (.id (java.util.Optional/empty)) (.createdAt 10)
                    (.name (java.util.Optional/empty)) (.build))
        full (-> (VideoCreateCharacterResponse/builder)
                 (.id "char_1") (.createdAt 10) (.name "Scout") (.build))]
    (is (= {:created-at 10} (when convert (convert minimal))))
    (is (= {:id "char_1" :created-at 10 :name "Scout"}
           (when convert (convert full))))))

(deftest converts-video-delete-response
  (let [convert (some-> (ns-resolve 'openai.videos 'delete-response->map) deref)
        response (-> (VideoDeleteResponse/builder)
                     (.id "video_1") (.deleted true)
                     (.object_ (JsonValue/from "video.deleted")) (.build))]
    (is (= {:id "video_1" :deleted true}
           (when convert (convert response))))))

(deftest converts-video-error-misalignment
  (let [misalignment (-> (com.openai.models.videos.VideoCreateError$Misalignment/builder)
                         (.detailedExplanation "The video request needs review.")
                         (.errorType "potentially_unintended_data_access")
                         (.steer (-> (com.openai.models.videos.VideoCreateError$Misalignment$Steer/builder)
                                     (.message "Please revise the prompt.")
                                     (.build)))
                         (.build))
        error (-> (VideoCreateError/builder)
                  (.code "render_failed")
                  (.message "Could not render")
                  (.misalignment misalignment)
                  (.build))]
    (is (= {:code "render_failed"
            :message "Could not render"
            :misalignment {:detailed-explanation "The video request needs review."
                           :error-type :potentially-unintended-data-access
                           :steer {:message "Please revise the prompt."}}}
           (#'videos/video-error->map error))))
  (let [mapped (#'videos/video-error->map
                (-> (VideoCreateError/builder)
                    (.code "render_failed")
                    (.message "Could not render")
                    (.build)))]
    (is (= {:code "render_failed" :message "Could not render"} mapped))
    (is (not (contains? mapped :misalignment)))))
