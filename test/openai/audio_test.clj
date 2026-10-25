(ns openai.audio-test
  (:require [clojure.test :refer [deftest is]]
            [openai.audio :as audio])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.audio AudioModel AudioResponseFormat)
           (com.openai.core JsonValue)
           (com.openai.models.audio.speech SpeechCreateParams
                                           SpeechCreateParams$ResponseFormat
                                           SpeechCreateParams$Voice
                                           SpeechModel)
           (com.openai.models.audio.transcriptions Transcription
                                                   TranscriptionCreateParams
                                                   TranscriptionCreateParams$TimestampGranularity
                                                   TranscriptionCreateResponse
                                                   TranscriptionVerbose)
           (com.openai.models.audio.translations Translation
                                                TranslationCreateParams
                                                TranslationCreateParams$ResponseFormat
                                                TranslationCreateResponse
                                                TranslationVerbose)
           (com.openai.models.audio.voiceconsents VoiceConsent VoiceConsentDeleted
                                                  VoiceConsentListPage
                                                  VoiceConsentListPageResponse)
           (com.openai.models.audio.voices Voice Voice$Type VoiceDeleted
                                          VoiceCreateParams
                                          VoiceCreateParams$Body$AudioSample$Type)
           (com.openai.services.blocking AudioService)
           (com.openai.services.blocking.audio VoiceConsentService VoiceService)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(defn- speech-params ^SpeechCreateParams [m]
  (#'audio/->speech-params m))

(defn- transcription-params ^TranscriptionCreateParams [m]
  (#'audio/->transcription-params m))

(defn- translation-params ^TranslationCreateParams [m]
  (#'audio/->translation-params m))

(defn- voice-params ^VoiceCreateParams [m]
  (#'audio/->voice-params m))

(deftest translates-speech-params
  (let [p (speech-params {:input "Hello"
                          :model "gpt-4o-mini-tts"
                          :voice :alloy
                          :response-format :mp3})]
    (is (= "Hello" (.input p)))
    (is (= "gpt-4o-mini-tts" (.asString ^SpeechModel (.model p))))
    (is (= "alloy" (.asString ^SpeechCreateParams$Voice (.voice p))))
    (is (= "mp3" (.asString ^SpeechCreateParams$ResponseFormat
                             (opt (.responseFormat p)))))))

(deftest coerces-transcription-bytes-with-filename-and-translates-options
  (let [p (transcription-params {:file (.getBytes "audio" "UTF-8")
                                 :filename "sample.wav"
                                 :model "whisper-1"
                                 :language "fr"
                                 :prompt "Names"
                                 :response-format :verbose-json
                                 :temperature 0.2
                                 :timestamp-granularities [:word :segment]})
        field (._file p)]
    (is (= "sample.wav" (opt (.filename field))))
    (is (= [97 117 100 105 111] (vec (.readAllBytes (.file p)))))
    (is (= "whisper-1" (.asString ^AudioModel (.model p))))
    (is (= "fr" (opt (.language p))))
    (is (= "Names" (opt (.prompt p))))
    (is (= "verbose_json" (.asString ^AudioResponseFormat
                                      (opt (.responseFormat p)))))
    (is (= 0.2 (opt (.temperature p))))
    (is (= ["word" "segment"]
           (mapv #(.asString ^TranscriptionCreateParams$TimestampGranularity %)
                 (opt (.timestampGranularities p)))))))

(deftest translates-transcription-languages
  (let [p (transcription-params {:file (.getBytes "audio" "UTF-8")
                                 :model "whisper-1"
                                 :language "fr"
                                 :languages ["fr" "en"]})]
    (is (= "fr" (opt (.language p))))
    (is (= ["fr" "en"] (opt (.languages p))))))

(deftest converts-transcription-response-unions
  (let [plain (-> (Transcription/builder) (.text "plain") (.build))
        verbose (-> (TranscriptionVerbose/builder)
                    (.text "verbose") (.language "en") (.duration 1.5) (.build))]
    (is (= {:text "plain"}
           (#'audio/transcription-response->map
            (TranscriptionCreateResponse/ofTranscription plain))))
    (is (= {:text "verbose" :language "en" :duration 1.5}
           (#'audio/transcription-response->map
            (TranscriptionCreateResponse/ofVerbose verbose))))))

(deftest translates-translation-params
  (let [p (translation-params {:file (.getBytes "audio" "UTF-8")
                               :filename "sample.mp3"
                               :model "whisper-1"
                               :prompt "Names"
                               :response-format :verbose-json
                               :temperature 0.3})]
    (is (= "sample.mp3" (opt (.filename (._file p)))))
    (is (= "whisper-1" (.asString ^AudioModel (.model p))))
    (is (= "Names" (opt (.prompt p))))
    (is (= "verbose_json"
           (.asString ^TranslationCreateParams$ResponseFormat
                      (opt (.responseFormat p)))))
    (is (= 0.3 (opt (.temperature p))))))

(deftest converts-translation-response-unions
  (let [plain (-> (Translation/builder) (.text "plain") (.build))
        verbose (-> (TranslationVerbose/builder)
                    (.text "verbose") (.language "en") (.duration 2.5) (.build))]
    (is (= {:text "plain"}
           (#'audio/translation-response->map
            (TranslationCreateResponse/ofTranslation plain))))
    (is (= {:text "verbose" :language "en" :duration 2.5}
           (#'audio/translation-response->map
            (TranslationCreateResponse/ofVerbose verbose))))))

(deftest builds-voice-audio-sample-params
  (let [p (voice-params {:type :audio-sample
                         :audio-sample (.getBytes "audio" "UTF-8")
                         :consent "I consent"
                         :name "Ada"})
        body (.asAudioSample (.body p))]
    (is (= "I consent" (.consent body)))
    (is (= "Ada" (.name body)))
    (is (= "audio_sample" (.asString ^VoiceCreateParams$Body$AudioSample$Type
                                       (opt (.type body)))))
    (is (= [97 117 100 105 111] (vec (.readAllBytes (.audioSample body)))))))

(deftest builds-voice-params-without-explicit-type
  (let [p (voice-params {:audio-sample (.getBytes "audio" "UTF-8")
                         :consent "I consent"
                         :name "Ada"})
        body (.asAudioSample (.body p))]
    (is (= "Ada" (.name body)))
    (is (= "I consent" (.consent body)))
    (is (.isAudioSample (.body p)))))

(deftest converts-voice-result
  (let [voice (-> (Voice/builder) (.id "voice_123") (.createdAt 42) (.name "Ada")
                  (.object_ (JsonValue/from "voice")) (.type Voice$Type/AUDIO_SAMPLE) (.build))]
    (is (= {:id "voice_123" :created-at 42 :name "Ada" :object "voice" :type :audio-sample}
           (#'audio/voice->map voice)))))

(deftest rejects-invalid-voice-variant
  (doseq [req [{:type :unsupported}]]
    (let [error (try (voice-params req) nil (catch clojure.lang.ExceptionInfo e e))]
      (is (= :invalid-voice-variant (:openai/error (ex-data error)))))))

(deftest builds-prompt-voice-params
  (let [p (voice-params {:type :prompt :name "Ada" :prompt "A warm narrator"
                         :model "gpt-4o-mini-tts" :script-hint "Hello"})
        body (.asPrompt (.body p))]
    (is (= "Ada" (.name body)))
    (is (= "A warm narrator" (.prompt body)))
    (is (= "gpt-4o-mini-tts" (.asString (opt (.model body)))))
    (is (= "Hello" (opt (.scriptHint body))))))

(deftest voice-consent-create-builds-params
  (let [p (#'audio/->voice-consent-params
           {:recording (.getBytes "consent" "UTF-8")
            :filename "consent.wav" :language "en" :name "Ada"})]
    (is (= "en" (.language p)))
    (is (= "Ada" (.name p)))
    (is (= [99 111 110 115 101 110 116] (vec (.readAllBytes (.recording p)))))))

(deftest voice-consent-requires-fields
  (let [error-data (fn [request]
                     (try
                       (#'audio/->voice-consent-params request)
                       nil
                       (catch clojure.lang.ExceptionInfo e (ex-data e))))]
    (is (= {:openai/error :missing-key :key :recording}
           (error-data {:language "en" :name "Ada"})))
    (is (= {:openai/error :missing-key :key :language}
           (error-data {:recording (.getBytes "x" "UTF-8") :name "Ada"})))
    (is (= {:openai/error :missing-key :key :name}
           (error-data {:recording (.getBytes "x" "UTF-8") :language "en"})))))

(deftest voice-consent-crud-round-trips
  (let [consent (-> (VoiceConsent/builder) (.id "consent_123") (.createdAt 42)
                    (.language "en") (.name "Ada") (.object_ (JsonValue/from "voice_consent")) (.build))
        deleted (-> (VoiceConsentDeleted/builder) (.id "consent_123") (.deleted true)
                    (.object_ (JsonValue/from "voice_consent")) (.build))
        response (-> (VoiceConsentListPageResponse/builder) (.addData consent)
                     (.hasMore false) (.object_ (JsonValue/from "list")) (.build))
        page (-> (VoiceConsentListPage/builder) (.response response)
                 (.params (com.openai.models.audio.voiceconsents.VoiceConsentListParams/none))
                 (.service (proxy [VoiceConsentService] [])) (.build))
        seen (atom [])
        service (proxy [VoiceConsentService] []
                  (create [params] (swap! seen conj [:create params]) consent)
                  (retrieve [id] (swap! seen conj [:retrieve id]) consent)
                  (list [_] (swap! seen conj [:list]) page)
                  (update [params] (swap! seen conj [:update params]) consent)
                  (delete [id] (swap! seen conj [:delete id]) deleted))
        audio-service (proxy [AudioService] [] (voiceConsents [] service))
        client (proxy [OpenAIClient] [] (audio [] audio-service))]
    (is (= {:id "consent_123" :created-at 42 :language "en" :name "Ada" :object "voice_consent"}
           (audio/create-voice-consent client {:recording (.getBytes "consent" "UTF-8")
                                               :language "en" :name "Ada"})))
    (is (= {:id "consent_123" :created-at 42 :language "en" :name "Ada" :object "voice_consent"}
           (audio/retrieve-voice-consent client "consent_123")))
    (is (= [{:id "consent_123" :created-at 42 :language "en" :name "Ada" :object "voice_consent"}]
           (audio/list-voice-consents client {:after "consent_000" :limit 10})))
    (is (= {:id "consent_123" :created-at 42 :language "en" :name "Ada" :object "voice_consent"}
           (audio/update-voice-consent client "consent_123" {:name "Ada Lovelace"})))
    (is (= {:id "consent_123" :deleted true :object "voice_consent"}
           (audio/delete-voice-consent client "consent_123")))
    (is (= [:create :retrieve :list :update :delete] (mapv first @seen)))))

(deftest voice-delete-round-trips
  (let [deleted (-> (VoiceDeleted/builder) (.id "voice_123") (.deleted true)
                    (.object_ (JsonValue/from "voice")) (.build))
        service (proxy [VoiceService] [] (delete [id] (is (= "voice_123" id)) deleted))
        audio-service (proxy [AudioService] [] (voices [] service))
        client (proxy [OpenAIClient] [] (audio [] audio-service))]
    (is (= {:id "voice_123" :deleted true :object "voice"}
           (audio/delete-voice client "voice_123")))))

(deftest creates-voice-through-the-audio-service
  (let [voice (-> (Voice/builder) (.id "voice_123") (.createdAt 42) (.name "Ada")
                  (.object_ (JsonValue/from "voice")) (.type Voice$Type/AUDIO_SAMPLE) (.build))
        service (proxy [VoiceService] [] (create [_] voice))
        audio-service (proxy [AudioService] [] (voices [] service))
        client (proxy [OpenAIClient] [] (audio [] audio-service))]
    (is (= {:id "voice_123" :created-at 42 :name "Ada" :object "voice" :type :audio-sample}
           (audio/create-voice client {:type :audio-sample
                                       :audio-sample (.getBytes "audio" "UTF-8")
                                       :consent "I consent"
                                       :name "Ada"})))))
