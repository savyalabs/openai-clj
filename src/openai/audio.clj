(ns openai.audio
  "Clojure wrapper for the OpenAI Audio API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core MultipartField MultipartField$Builder)
           (com.openai.core.http HttpResponse StreamResponse)
           (com.openai.models.audio AudioResponseFormat)
           (com.openai.models.audio.voices Voice
                                           VoiceCreateParams
                                           VoiceCreateParams$Builder
                                           VoiceCreateParams$Body$AudioSample
                                           VoiceCreateParams$Body$AudioSample$Type)
           (com.openai.models.audio.speech SpeechCreateParams
                                           SpeechCreateParams$Builder
                                           SpeechCreateParams$ResponseFormat
                                           SpeechCreateParams$StreamFormat
                                           SpeechCreateParams$Voice)
           (com.openai.models.audio.transcriptions Transcription
                                                   Transcription$Logprob
                                                   Transcription$Usage
                                                   Transcription$Usage$Duration
                                                   Transcription$Usage$Tokens
                                                   TranscriptionCreateParams
                                                   TranscriptionCreateParams$Builder
                                                   TranscriptionCreateParams$ChunkingStrategy
                                                   TranscriptionCreateParams$ChunkingStrategy$VadConfig
                                                   TranscriptionCreateParams$ChunkingStrategy$VadConfig$Builder
                                                   TranscriptionCreateParams$ChunkingStrategy$VadConfig$Type
                                                   TranscriptionCreateParams$TimestampGranularity
                                                   TranscriptionCreateResponse
                                                   TranscriptionInclude
                                                   TranscriptionSegment
                                                   TranscriptionStreamEvent
                                                   TranscriptionTextDeltaEvent
                                                   TranscriptionTextDoneEvent
                                                   TranscriptionTextSegmentEvent
                                                   TranscriptionVerbose
                                                   TranscriptionVerbose$Usage
                                                   TranscriptionWord)
           (com.openai.models.audio.translations Translation
                                                TranslationCreateParams
                                                TranslationCreateParams$Builder
                                                TranslationCreateParams$ResponseFormat
                                                TranslationCreateResponse
                                                TranslationVerbose)
           (com.openai.services.blocking AudioService)
           (com.openai.services.blocking.audio SpeechService
                                                 TranscriptionService
                                                 TranslationService
                                                 VoiceService)
           (java.io ByteArrayInputStream File InputStream)
           (java.nio.file Files Path)
           (java.util.stream Stream)))

(set! *warn-on-reflection* true)

(defn- ->input-stream ^InputStream [input]
  (cond
    (instance? InputStream input) input
    (bytes? input) (ByteArrayInputStream. ^bytes input)
    (instance? Path input) (Files/newInputStream ^Path input (make-array java.nio.file.OpenOption 0))
    (string? input) (Files/newInputStream (.toPath (File. ^String input)) (make-array java.nio.file.OpenOption 0))
    :else (throw (ex-info (str "Unsupported audio file type " (class input))
                          {:openai/error :unsupported-file-type :class (class input)}))))

(defn- ->file-field ^MultipartField [input filename]
  (let [^MultipartField$Builder b (MultipartField/builder)]
    (.value b (->input-stream input))
    (when filename (.filename b ^String filename))
    (.build b)))

(defn- set-transcription-file! [^TranscriptionCreateParams$Builder b file filename]
  (cond
    filename (.file b (->file-field file filename))
    (instance? Path file) (.file b ^Path file)
    (string? file) (.file b (.toPath (File. ^String file)))
    (bytes? file) (.file b ^bytes file)
    :else (.file b ^InputStream file)))

(defn- set-translation-file! [^TranslationCreateParams$Builder b file filename]
  (cond
    filename (.file b (->file-field file filename))
    (instance? Path file) (.file b ^Path file)
    (string? file) (.file b (.toPath (File. ^String file)))
    (bytes? file) (.file b ^bytes file)
    :else (.file b ^InputStream file)))

(defn- ->speech-params ^SpeechCreateParams
  [{:keys [input model voice response-format speed instructions stream-format]}]
  (when-not input (impl/missing-key! :input))
  (when-not model (impl/missing-key! :model))
  (when-not voice (impl/missing-key! :voice))
  (let [^SpeechCreateParams$Builder b (SpeechCreateParams/builder)]
    (.input b ^String input)
    (.model b ^String model)
    (.voice b (SpeechCreateParams$Voice/ofString (name voice)))
    (when response-format (.responseFormat b (SpeechCreateParams$ResponseFormat/of (impl/enum-name response-format))))
    (when speed (.speed b (double speed)))
    (when instructions (.instructions b ^String instructions))
    (when stream-format (.streamFormat b (SpeechCreateParams$StreamFormat/of (impl/enum-name stream-format))))
    (.build b)))

(defn- ->vad-config ^TranscriptionCreateParams$ChunkingStrategy$VadConfig
  [{:keys [prefix-padding-ms silence-duration-ms threshold]}]
  (let [^TranscriptionCreateParams$ChunkingStrategy$VadConfig$Builder b
        (TranscriptionCreateParams$ChunkingStrategy$VadConfig/builder)]
    (.type b (TranscriptionCreateParams$ChunkingStrategy$VadConfig$Type/of "server_vad"))
    (when prefix-padding-ms (.prefixPaddingMs b (long prefix-padding-ms)))
    (when silence-duration-ms (.silenceDurationMs b (long silence-duration-ms)))
    (when threshold (.threshold b (double threshold)))
    (.build b)))

(defn- ->chunking-strategy ^TranscriptionCreateParams$ChunkingStrategy [x]
  (cond
    (= :auto x) (TranscriptionCreateParams$ChunkingStrategy/ofAuto)
    (map? x) (TranscriptionCreateParams$ChunkingStrategy/ofVadConfig (->vad-config x))
    (instance? TranscriptionCreateParams$ChunkingStrategy x) x
    :else (throw (ex-info "Unsupported chunking strategy"
                          {:openai/error :unsupported-chunking-strategy :value x}))))

(defn- ->transcription-params ^TranscriptionCreateParams
  [{:keys [file filename model language languages prompt response-format temperature
           timestamp-granularities include chunking-strategy]}]
  (when-not file (impl/missing-key! :file))
  (when-not model (impl/missing-key! :model))
  (let [^TranscriptionCreateParams$Builder b (TranscriptionCreateParams/builder)]
    (set-transcription-file! b file filename)
    (.model b ^String model)
    (when language (.language b ^String language))
    (when languages (.languages b ^java.util.List (vec languages)))
    (when prompt (.prompt b ^String prompt))
    (when response-format (.responseFormat b (AudioResponseFormat/of (impl/enum-name response-format))))
    (when temperature (.temperature b (double temperature)))
    (when timestamp-granularities
      (.timestampGranularities
       b
       ^java.util.List (mapv #(TranscriptionCreateParams$TimestampGranularity/of (name %))
                             timestamp-granularities)))
    (when include
      (.include b ^java.util.List (mapv #(TranscriptionInclude/of (impl/enum-name %)) include)))
    (when chunking-strategy (.chunkingStrategy b (->chunking-strategy chunking-strategy)))
    (.build b)))

(defn- segment->map [^TranscriptionSegment x]
  {:id (.id x) :avg-logprob (.avgLogprob x) :compression-ratio (.compressionRatio x)
   :end (.end x) :no-speech-prob (.noSpeechProb x) :seek (.seek x)
   :start (.start x) :temperature (.temperature x) :text (.text x)
   :tokens (vec (.tokens x))})

(defn- word->map [^TranscriptionWord x]
  {:end (.end x) :start (.start x) :word (.word x)})

(defn- logprob->map [^Transcription$Logprob x]
  (cond-> {}
    (.isPresent (.token x)) (assoc :token (impl/opt-get (.token x)))
    (.isPresent (.bytes x)) (assoc :bytes (vec (impl/opt-get (.bytes x))))
    (.isPresent (.logprob x)) (assoc :logprob (impl/opt-get (.logprob x)))))

(defn- usage->map [^Transcription$Usage x]
  (cond
    (.isTokens x) (let [^Transcription$Usage$Tokens u (.asTokens x)]
                    {:input-tokens (.inputTokens u) :output-tokens (.outputTokens u)
                     :total-tokens (.totalTokens u)})
    (.isDuration x) {:seconds (.seconds ^Transcription$Usage$Duration (.asDuration x))}))

(defn- transcription-response->map [^TranscriptionCreateResponse response]
  (cond
    (.isTranscription response)
    (let [^Transcription x (.asTranscription response)]
      (cond-> {:text (.text x)}
        (.isPresent (.logprobs x)) (assoc :logprobs (mapv logprob->map (impl/opt-get (.logprobs x))))
        (.isPresent (.usage x)) (assoc :usage (usage->map (impl/opt-get (.usage x))))))

    (.isVerbose response)
    (let [^TranscriptionVerbose x (.asVerbose response)]
      (cond-> {:text (.text x) :language (.language x) :duration (.duration x)}
        (.isPresent (.segments x)) (assoc :segments (mapv segment->map (impl/opt-get (.segments x))))
        (.isPresent (.words x)) (assoc :words (mapv word->map (impl/opt-get (.words x))))
        (.isPresent (.usage x)) (assoc :usage {:seconds (.seconds ^TranscriptionVerbose$Usage
                                                                  (impl/opt-get (.usage x)))})))

    :else (throw (ex-info "Unsupported transcription response variant"
                          {:openai/error :unsupported-response-variant}))))

(defn- ->translation-params ^TranslationCreateParams
  [{:keys [file filename model prompt response-format temperature]}]
  (when-not file (impl/missing-key! :file))
  (when-not model (impl/missing-key! :model))
  (let [^TranslationCreateParams$Builder b (TranslationCreateParams/builder)]
    (set-translation-file! b file filename)
    (.model b ^String model)
    (when prompt (.prompt b ^String prompt))
    (when response-format (.responseFormat b (TranslationCreateParams$ResponseFormat/of (impl/enum-name response-format))))
    (when temperature (.temperature b (double temperature)))
    (.build b)))

(defn- translation-response->map [^TranslationCreateResponse response]
  (if (.isTranslation response)
    {:text (.text ^Translation (.asTranslation response))}
    (let [^TranslationVerbose x (.asVerbose response)]
      (cond-> {:text (.text x) :language (.language x) :duration (.duration x)}
        (.isPresent (.segments x)) (assoc :segments (mapv segment->map (impl/opt-get (.segments x))))))))

(defn- invalid-voice-variant! [type]
  (throw (ex-info "Unsupported voice creation variant"
                  {:openai/error :invalid-voice-variant :type type})))

(defn- ->voice-params ^VoiceCreateParams
  [{:keys [type audio-sample consent name]}]
  (let [^VoiceCreateParams$Builder b (VoiceCreateParams/builder)]
    (when-not (or (nil? type) (= :audio-sample type))
      (invalid-voice-variant! type))
    (when-not audio-sample (impl/missing-key! :audio-sample))
    (when-not consent (impl/missing-key! :consent))
    (when-not name (impl/missing-key! :name))
    (.body b
           (let [body (VoiceCreateParams$Body$AudioSample/builder)]
             (.audioSample body (->input-stream audio-sample))
             (.consent body ^String consent)
             (.name body ^String name)
             (.type body (VoiceCreateParams$Body$AudioSample$Type/of "audio_sample"))
             (.build body)))
    (.build b)))

(defn- voice->map [^Voice voice]
  {:id (.id voice) :created-at (.createdAt voice) :name (.name voice)
   :object (impl/json-value->clj (._object_ voice))
   :type (impl/->keyword (.asString (.type voice)))})

(defn- transcription-event->map [^TranscriptionStreamEvent event]
  (cond
    (.isTranscriptTextDelta event)
    (let [^TranscriptionTextDeltaEvent x (.asTranscriptTextDelta event)]
      (cond-> {:type :text-delta :delta (.delta x)}
        (.isPresent (.segmentId x)) (assoc :segment-id (impl/opt-get (.segmentId x)))))
    (.isTranscriptTextDone event)
    {:type :text-done :text (.text ^TranscriptionTextDoneEvent (.asTranscriptTextDone event))}
    (.isTranscriptTextSegment event)
    (let [^TranscriptionTextSegmentEvent x (.asTranscriptTextSegment event)]
      {:type :text-segment :id (.id x) :start (.start x) :end (.end x)
       :speaker (.speaker x) :text (.text x)})))

(defn create-speech
  "Create speech audio and return it as a byte array."
  ^bytes [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^AudioService audio (.audio client)
          ^SpeechService svc (.speech audio)]
      (with-open [^HttpResponse response (.create svc (->speech-params req))]
        (.readAllBytes (.body response))))))

(defn create-transcription
  "Transcribe an audio file and return a normalized response map."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^AudioService audio (.audio client)
          ^TranscriptionService svc (.transcriptions audio)]
      (transcription-response->map (.create svc (->transcription-params req))))))

(defn create-voice
  "Create a custom voice from an audio sample and return a normalized response map.

   As of SDK 4.81.0, only audio-sample voice creation is supported."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^AudioService audio (.audio client)
          ^VoiceService svc (.voices audio)]
      (voice->map (.create svc (->voice-params req))))))

(defn create-transcription-streaming
  "Stream a transcription. Call `on-event` and return concatenated text deltas."
  ^String [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^AudioService audio (.audio client)
          ^TranscriptionService svc (.transcriptions audio)]
      (with-open [^StreamResponse response (.createStreaming svc (->transcription-params req))]
        (let [sb (StringBuilder.)
              ^Stream stream (.stream response)]
          (doseq [event (iterator-seq (.iterator stream))]
            (let [m (transcription-event->map event)]
              (when (= :text-delta (:type m)) (.append sb ^String (:delta m)))
              (when on-event (on-event m))))
          (str sb))))))

(defn create-translation
  "Translate an audio file to English and return a normalized response map."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^AudioService audio (.audio client)
          ^TranslationService svc (.translations audio)]
      (translation-response->map (.create svc (->translation-params req))))))
