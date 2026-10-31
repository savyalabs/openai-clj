(ns openai.images
  "Clojure wrapper for the OpenAI Images API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core MultipartField MultipartField$Builder)
           (com.openai.core.http StreamResponse)
           (com.openai.models.images Image
                                      ImageCreateVariationParams
                                      ImageCreateVariationParams$Builder
                                      ImageCreateVariationParams$ResponseFormat
                                      ImageCreateVariationParams$Size
                                      ImageEditCompletedEvent
                                      ImageEditCompletedEvent$Usage
                                      ImageEditCompletedEvent$Usage$InputTokensDetails
                                      ImageEditParams
                                      ImageEditParams$Background
                                      ImageEditParams$Builder
                                      ImageEditParams$Image
                                      ImageEditParams$InputFidelity
                                      ImageEditParams$OutputFormat
                                      ImageEditParams$Quality
                                      ImageEditParams$ResponseFormat
                                      ImageEditParams$Size
                                      ImageEditPartialImageEvent
                                      ImageEditStreamEvent
                                      ImageGenCompletedEvent
                                      ImageGenCompletedEvent$Usage
                                      ImageGenCompletedEvent$Usage$InputTokensDetails
                                      ImageGenerateParams
                                      ImageGenerateParams$Background
                                      ImageGenerateParams$Builder
                                      ImageGenerateParams$Moderation
                                      ImageGenerateParams$OutputFormat
                                      ImageGenerateParams$Quality
                                      ImageGenerateParams$ResponseFormat
                                      ImageGenerateParams$Size
                                      ImageGenerateParams$Style
                                      ImageGenPartialImageEvent
                                      ImageGenStreamEvent
                                      ImagesResponse
                                      ImagesResponse$Background
                                      ImagesResponse$OutputFormat
                                      ImagesResponse$Quality
                                      ImagesResponse$Size
                                      ImagesResponse$Usage
                                      ImagesResponse$Usage$InputTokensDetails
                                      ImagesResponse$Usage$OutputTokensDetails)
           (com.openai.services.blocking ImageService)
           (java.io ByteArrayInputStream File InputStream)
           (java.nio.file Files Path)
           (java.util.stream Stream)))

(set! *warn-on-reflection* true)

(defn- ->generate-params ^ImageGenerateParams
  [{:keys [prompt model n size quality style response-format background
           output-format output-compression moderation partial-images safety-identifier user]}]
  (when-not prompt (impl/missing-key! :prompt))
  (let [^ImageGenerateParams$Builder b (ImageGenerateParams/builder)]
    (.prompt b ^String prompt)
    (when model (.model b ^String model))
    (when n (.n b (long n)))
    (when size (.size b (ImageGenerateParams$Size/of (impl/enum-name size))))
    (when quality (.quality b (ImageGenerateParams$Quality/of (impl/enum-name quality))))
    (when style (.style b (ImageGenerateParams$Style/of (impl/enum-name style))))
    (when response-format (.responseFormat b (ImageGenerateParams$ResponseFormat/of (impl/enum-name response-format))))
    (when background (.background b (ImageGenerateParams$Background/of (impl/enum-name background))))
    (when output-format (.outputFormat b (ImageGenerateParams$OutputFormat/of (impl/enum-name output-format))))
    (when output-compression (.outputCompression b (long output-compression)))
    (when moderation (.moderation b (ImageGenerateParams$Moderation/of (impl/enum-name moderation))))
    (when partial-images (.partialImages b (long partial-images)))
    (when safety-identifier (.safetyIdentifier b ^String safety-identifier))
    (when user (.user b ^String user))
    (.build b)))

(defn- ->input-stream ^InputStream [input]
  (cond
    (instance? InputStream input) input
    (bytes? input) (ByteArrayInputStream. ^bytes input)
    (instance? Path input) (Files/newInputStream ^Path input (make-array java.nio.file.OpenOption 0))
    (string? input) (Files/newInputStream (.toPath (File. ^String input)) (make-array java.nio.file.OpenOption 0))
    :else (throw (ex-info (str "Unsupported image type " (class input))
                          {:openai/error :unsupported-file-type
                           :class (class input)}))))

(defn- multipart-field ^MultipartField [value filename]
  (let [^MultipartField$Builder b (MultipartField/builder)]
    (.value b value)
    (when filename (.filename b ^String filename))
    (.build b)))

(defn- ->edit-image-field ^MultipartField [image filename]
  (let [value (if (vector? image)
                (ImageEditParams$Image/ofInputStreams
                 (mapv ->input-stream image))
                (ImageEditParams$Image/ofInputStream (->input-stream image)))]
    (multipart-field value filename)))

(defn- ->file-field ^MultipartField [input filename]
  (multipart-field (->input-stream input) filename))

(defn- set-edit-image! [^ImageEditParams$Builder b image filename]
  (cond
    (or filename (vector? image)) (.image b (->edit-image-field image filename))
    (instance? Path image) (.image b ^Path image)
    (string? image) (.image b (.toPath (File. ^String image)))
    :else (.image b (->input-stream image))))

(defn- set-mask! [^ImageEditParams$Builder b mask filename]
  (cond
    filename (.mask b (->file-field mask filename))
    (instance? Path mask) (.mask b ^Path mask)
    (string? mask) (.mask b (.toPath (File. ^String mask)))
    :else (.mask b (->input-stream mask))))

(defn- set-markup! [^ImageEditParams$Builder b markup filename]
  (cond
    filename (.markup b (->file-field markup filename))
    (instance? Path markup) (.markup b ^Path markup)
    (string? markup) (.markup b (.toPath (File. ^String markup)))
    :else (.markup b (->input-stream markup))))

(defn- set-variation-image! [^ImageCreateVariationParams$Builder b image filename]
  (cond
    filename (.image b (->file-field image filename))
    (instance? Path image) (.image b ^Path image)
    (string? image) (.image b (.toPath (File. ^String image)))
    :else (.image b (->input-stream image))))

(defn- ->edit-params ^ImageEditParams
  [{:keys [image filename markup markup-filename mask mask-filename prompt model n size response-format
           background input-fidelity output-compression output-format
           partial-images quality safety-identifier user]}]
  (when-not image (impl/missing-key! :image))
  (when-not prompt (impl/missing-key! :prompt))
  (let [^ImageEditParams$Builder b (ImageEditParams/builder)]
    (set-edit-image! b image filename)
    (.prompt b ^String prompt)
    (when markup (set-markup! b markup markup-filename))
    (when mask (set-mask! b mask mask-filename))
    (when model (.model b ^String model))
    (when n (.n b (long n)))
    (when size (.size b (ImageEditParams$Size/of (impl/enum-name size))))
    (when response-format (.responseFormat b (ImageEditParams$ResponseFormat/of (impl/enum-name response-format))))
    (when background (.background b (ImageEditParams$Background/of (impl/enum-name background))))
    (when input-fidelity (.inputFidelity b (ImageEditParams$InputFidelity/of (impl/enum-name input-fidelity))))
    (when output-compression (.outputCompression b (long output-compression)))
    (when output-format (.outputFormat b (ImageEditParams$OutputFormat/of (impl/enum-name output-format))))
    (when partial-images (.partialImages b (long partial-images)))
    (when quality (.quality b (ImageEditParams$Quality/of (impl/enum-name quality))))
    (when safety-identifier (.safetyIdentifier b ^String safety-identifier))
    (when user (.user b ^String user))
    (.build b)))

(defn- ->create-variation-params ^ImageCreateVariationParams
  [{:keys [image filename model n size response-format user]}]
  (when-not image (impl/missing-key! :image))
  (let [^ImageCreateVariationParams$Builder b (ImageCreateVariationParams/builder)]
    (set-variation-image! b image filename)
    (when model (.model b ^String model))
    (when n (.n b (long n)))
    (when size (.size b (ImageCreateVariationParams$Size/of (impl/enum-name size))))
    (when response-format (.responseFormat b (ImageCreateVariationParams$ResponseFormat/of (impl/enum-name response-format))))
    (when user (.user b ^String user))
    (.build b)))

(defn- image->map [^Image image]
  (cond-> {}
    (.isPresent (.b64Json image)) (assoc :b64-json (impl/opt-get (.b64Json image)))
    (.isPresent (.url image)) (assoc :url (impl/opt-get (.url image)))
    (.isPresent (.revisedPrompt image)) (assoc :revised-prompt (impl/opt-get (.revisedPrompt image)))))

(defn- token-details->map [details]
  (condp instance? details
    ImagesResponse$Usage$InputTokensDetails
    {:image-tokens (.imageTokens ^ImagesResponse$Usage$InputTokensDetails details)
     :text-tokens (.textTokens ^ImagesResponse$Usage$InputTokensDetails details)}
    ImagesResponse$Usage$OutputTokensDetails
    {:image-tokens (.imageTokens ^ImagesResponse$Usage$OutputTokensDetails details)
     :text-tokens (.textTokens ^ImagesResponse$Usage$OutputTokensDetails details)}))

(defn- usage->map [^ImagesResponse$Usage usage]
  (cond-> {:input-tokens (.inputTokens usage)
           :input-tokens-details (token-details->map (.inputTokensDetails usage))
           :output-tokens (.outputTokens usage)
           :total-tokens (.totalTokens usage)}
    (.isPresent (.outputTokensDetails usage))
    (assoc :output-tokens-details
           (token-details->map (impl/opt-get (.outputTokensDetails usage))))))

(defn- event-usage->map [usage]
  (condp instance? usage
    ImageGenCompletedEvent$Usage
    (let [^ImageGenCompletedEvent$Usage u usage
          ^ImageGenCompletedEvent$Usage$InputTokensDetails d (.inputTokensDetails u)]
      {:input-tokens (.inputTokens u)
       :input-tokens-details {:image-tokens (.imageTokens d)
                              :text-tokens (.textTokens d)}
       :output-tokens (.outputTokens u)
       :total-tokens (.totalTokens u)})
    ImageEditCompletedEvent$Usage
    (let [^ImageEditCompletedEvent$Usage u usage
          ^ImageEditCompletedEvent$Usage$InputTokensDetails d (.inputTokensDetails u)]
      {:input-tokens (.inputTokens u)
       :input-tokens-details {:image-tokens (.imageTokens d)
                              :text-tokens (.textTokens d)}
       :output-tokens (.outputTokens u)
       :total-tokens (.totalTokens u)})))

(defn- images-response->map [^ImagesResponse response]
  (cond-> {:created (.created response)}
    (.isPresent (.data response))
    (assoc :data (mapv image->map (impl/opt-get (.data response))))
    (.isPresent (.background response))
    (assoc :background (impl/->keyword (.asString ^ImagesResponse$Background (impl/opt-get (.background response)))))
    (.isPresent (.outputFormat response))
    (assoc :output-format (impl/->keyword (.asString ^ImagesResponse$OutputFormat (impl/opt-get (.outputFormat response)))))
    (.isPresent (.quality response))
    (assoc :quality (impl/->keyword (.asString ^ImagesResponse$Quality (impl/opt-get (.quality response)))))
    (.isPresent (.size response))
    (assoc :size (impl/->keyword (.asString ^ImagesResponse$Size (impl/opt-get (.size response)))))
    (.isPresent (.usage response))
    (assoc :usage (usage->map (impl/opt-get (.usage response))))
    (.isPresent (.safetyIdentifier response))
    (assoc :safety-identifier (impl/opt-get (.safetyIdentifier response)))))

(defn- partial-event->map [event]
  (condp instance? event
    ImageGenPartialImageEvent
    (let [^ImageGenPartialImageEvent e event]
      {:type :partial-image :index (.partialImageIndex e) :b64-json (.b64Json e)
       :created-at (.createdAt e) :background (impl/->keyword (.asString (.background e)))
       :output-format (impl/->keyword (.asString (.outputFormat e)))
       :quality (impl/->keyword (.asString (.quality e))) :size (impl/->keyword (.asString (.size e)))})
    ImageEditPartialImageEvent
    (let [^ImageEditPartialImageEvent e event]
      {:type :partial-image :index (.partialImageIndex e) :b64-json (.b64Json e)
       :created-at (.createdAt e) :background (impl/->keyword (.asString (.background e)))
       :output-format (impl/->keyword (.asString (.outputFormat e)))
       :quality (impl/->keyword (.asString (.quality e))) :size (impl/->keyword (.asString (.size e)))})))

(defn- completed-event->map [event]
  (condp instance? event
    ImageGenCompletedEvent
    (let [^ImageGenCompletedEvent e event]
      (cond-> {:type :completed :b64-json (.b64Json e) :created-at (.createdAt e)
               :background (impl/->keyword (.asString (.background e)))
               :output-format (impl/->keyword (.asString (.outputFormat e)))
               :quality (impl/->keyword (.asString (.quality e)))
               :size (impl/->keyword (.asString (.size e)))
               :usage (event-usage->map (.usage e))}
        (.isPresent (.safetyIdentifier e))
        (assoc :safety-identifier (impl/opt-get (.safetyIdentifier e)))))
    ImageEditCompletedEvent
    (let [^ImageEditCompletedEvent e event]
      (cond-> {:type :completed :b64-json (.b64Json e) :created-at (.createdAt e)
               :background (impl/->keyword (.asString (.background e)))
               :output-format (impl/->keyword (.asString (.outputFormat e)))
               :quality (impl/->keyword (.asString (.quality e)))
               :size (impl/->keyword (.asString (.size e)))
               :usage (event-usage->map (.usage e))}
        (.isPresent (.safetyIdentifier e))
        (assoc :safety-identifier (impl/opt-get (.safetyIdentifier e)))))))

(defn- generate-event->map [^ImageGenStreamEvent event]
  (if (.isGenerationPartialImage event)
    (partial-event->map (.asGenerationPartialImage event))
    (completed-event->map (.asGenerationCompleted event))))

(defn- edit-event->map [^ImageEditStreamEvent event]
  (if (.isPartialImage event)
    (partial-event->map (.asPartialImage event))
    (completed-event->map (.asCompleted event))))

(defn- drain-stream [^StreamResponse response event->map on-event]
  (with-open [r response]
    (mapv (fn [event]
            (let [m (event->map event)]
              (on-event m)
              m))
          (.toList ^Stream (.stream r)))))

(defn generate [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ImageService service (.images client)]
      (images-response->map (.generate service (->generate-params req))))))

(defn edit [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ImageService service (.images client)]
      (images-response->map (.edit service (->edit-params req))))))

(defn create-variation [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ImageService service (.images client)]
      (images-response->map (.createVariation service (->create-variation-params req))))))

(defn generate-streaming [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^ImageService service (.images client)]
      (drain-stream (.generateStreaming service (->generate-params req))
                    generate-event->map on-event))))

(defn edit-streaming [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^ImageService service (.images client)]
      (drain-stream (.editStreaming service (->edit-params req))
                    edit-event->map on-event))))
