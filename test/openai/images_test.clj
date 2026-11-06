(ns openai.images-test
  (:require [clojure.test :refer [deftest is]]
            [openai.images :as images])
  (:import (com.openai.models.images Image
                                     ImageEditParams
                                     ImageGenerateParams
                                     ImageGenerateParams$Background
                                     ImageGenerateParams$Moderation
                                     ImageGenerateParams$OutputFormat
                                     ImageGenerateParams$Quality
                                     ImageGenerateParams$ResponseFormat
                                     ImageGenerateParams$Size
                                     ImageGenerateParams$Style
                                     ImageModel
                                     ImagesResponse)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(defn- generate-params ^ImageGenerateParams [m]
  (#'images/->generate-params m))

(defn- edit-params ^ImageEditParams [m]
  (#'images/->edit-params m))

(defn- response->map [^ImagesResponse response]
  (#'images/images-response->map response))

(deftest translates-generate-prompt-and-enums
  (let [p (generate-params {:prompt "a red fox"
                            :model "gpt-image-1"
                            :size :1024x1536
                            :quality :high
                            :style :vivid
                            :response-format :b64-json
                            :background :transparent
                            :output-format :webp
                            :moderation :low
                            :safety-identifier "safe-user"
                            :partial-images 2})]
    (is (= "a red fox" (.prompt p)))
    (is (= "gpt-image-1" (.asString ^ImageModel (opt (.model p)))))
    (is (= "1024x1536" (.asString ^ImageGenerateParams$Size (opt (.size p)))))
    (is (= "high" (.asString ^ImageGenerateParams$Quality (opt (.quality p)))))
    (is (= "vivid" (.asString ^ImageGenerateParams$Style (opt (.style p)))))
    (is (= "b64_json" (.asString ^ImageGenerateParams$ResponseFormat (opt (.responseFormat p)))))
    (is (= "transparent" (.asString ^ImageGenerateParams$Background (opt (.background p)))))
    (is (= "webp" (.asString ^ImageGenerateParams$OutputFormat (opt (.outputFormat p)))))
    (is (= "low" (.asString ^ImageGenerateParams$Moderation (opt (.moderation p)))))
    (is (= "safe-user" (opt (.safetyIdentifier p))))
    (is (= 2 (opt (.partialImages p))))))

(deftest translates-generate-animation-options
  (let [p (generate-params {:prompt "an animated fox"
                            :complexity :high
                            :frame-count 12
                            :frame-duration-ms 80})]
    (is (= "high" (.asString (opt (.complexity p)))))
    (is (= 12 (opt (.frameCount p))))
    (is (= 80 (opt (.frameDurationMs p))))))

(deftest translates-generate-svg-options
  (let [p (generate-params {:prompt "a fox as SVG"
                            :svg {:model "gpt-image-2.6"
                                  :reasoning {:effort :medium}}})
        svg (opt (.svg p))]
    (is (= "gpt-image-2.6" (.model svg)))
    (is (= "medium" (.asString (opt (-> svg .reasoning opt .effort)))))))

(deftest coerces-edit-bytes-with-filename
  (let [bytes (.getBytes "png" "UTF-8")
        p (edit-params {:image bytes
                        :filename "input.png"
                        :prompt "remove background"})
        field (._image p)]
    (is (= "input.png" (opt (.filename field))))
    (is (= [112 110 103]
           (vec (.readAllBytes (.asInputStream (.image p))))))))

(deftest image-edit-markup-overlay-builds-params
  (let [p (edit-params {:image (.getBytes "png" "UTF-8")
                        :prompt "annotate"
                        :markup (.getBytes "svg" "UTF-8")
                        :markup-filename "overlay.svg"})
        field (._markup p)]
    (is (= "overlay.svg" (opt (.filename field))))
    (is (= [115 118 103]
           (vec (.readAllBytes (opt (.markup p))))))))

(deftest translates-edit-animation-options
  (let [p (edit-params {:image (.getBytes "png" "UTF-8")
                        :prompt "animate"
                        :complexity :medium
                        :frame-count 8
                        :frame-duration-ms 100})]
    (is (= "medium" (.asString (opt (.complexity p)))))
    (is (= 8 (opt (.frameCount p))))
    (is (= 100 (opt (.frameDurationMs p))))))

(deftest converts-images-response
  (let [image (-> (Image/builder)
                  (.b64Json "encoded")
                  (.url "https://example.test/image.png")
                  (.revisedPrompt "a revised prompt")
                  (.build))
        response (-> (ImagesResponse/builder)
                     (.created 42)
                     (.addData image)
                     (.build))]
    (is (= {:created 42
            :data [{:b64-json "encoded"
                    :url "https://example.test/image.png"
                    :revised-prompt "a revised prompt"}]}
           (response->map response)))))

(deftest converts-images-response-svg-usage
  (let [svg-usage (-> (com.openai.models.images.ImagesResponse$SvgUsage/builder)
                      (.model "gpt-image-2.6")
                      (.inputTokens 11)
                      (.outputTokens 22)
                      (.reasoningTokens 3)
                      (.totalTokens 33)
                      (.build))
        response (-> (ImagesResponse/builder)
                     (.created 42)
                     (.svgUsage svg-usage)
                     (.build))]
    (is (= {:model "gpt-image-2.6"
            :input-tokens 11
            :output-tokens 22
            :reasoning-tokens 3
            :total-tokens 33}
           (:svg-usage (response->map response))))))
