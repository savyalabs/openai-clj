(ns openai.live-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [openai.live :as live])
  (:import (java.io ByteArrayInputStream)
           (com.openai.client OpenAIClient)
           (com.openai.models.live LiveCreateParams
                                   LiveCreateResponse
                                   LiveCreateResponse$Session
                                   LiveCreateResponse$Transport
                                   MediaSessionForkConfig)
           (com.openai.models.live.sessions SessionAcceptParams
                                             SessionHangupParams
                                             SessionReferParams
                                             SessionRejectParams
                                             SessionForkParams
                                             SessionDownloadRecordingParams
                                             SessionForkResponse
                                             SessionForkResponse$Session
                                             SessionForkResponse$Transport)
           (com.openai.services.blocking LiveService)
           (com.openai.services.blocking.live SessionService)))

(set! *warn-on-reflection* true)

(def mapper (json/object-mapper {:decode-key-fn true}))

(defn- json-value->clj [x]
  (json/read-value (json/write-value-as-string x) mapper))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(deftest validates-live-create-required-fields
  (doseq [[request key]
          [[{} :session]
           [{:session {}} :model]
           [{:session {:model "gpt-live-1"}} :transport]
           [{:session {:model "gpt-live-1"} :transport {}} :sdp]]]
    (is (= {:openai/error :missing-key :key key}
           (error-data #(#'live/->live-create-params request))))))

(deftest creates-live-session-and-normalizes-response
  (let [build-params (ns-resolve 'openai.live '->live-create-params)
        live-create (ns-resolve 'openai.live 'live-create)]
    (if (and build-params live-create)
      (let [request {:session {:model :gpt-live-1
                               :instructions "Be concise."
                               :audio {:input {:format {:type :audio/pcm
                                                        :rate 24000}}}}
                     :transport {:sdp "offer"}}
            ^LiveCreateParams params (build-params request)
            response (-> (LiveCreateResponse/builder)
                         (.session (-> (LiveCreateResponse$Session/builder)
                                       (.id "sess_1")
                                       (.build)))
                         (.transport (-> (LiveCreateResponse$Transport/builder)
                                         (.sdp "answer")
                                         (.build)))
                         (.build))
            captured (atom nil)
            service (proxy [LiveService] []
                      (create [p] (reset! captured p) response))
            client (proxy [OpenAIClient] []
                     (live [] service))]
        (testing "request parameters"
          (is (= "gpt-live-1" (-> params .session .model .asString)))
          (is (= "Be concise."
                 (-> params .session ._additionalProperties
                     (get "instructions") json-value->clj)))
          (is (= {:input {:format {:type "audio/pcm" :rate 24000}}}
                 (-> params .session ._additionalProperties
                     (get "audio") json-value->clj)))
          (is (= "offer" (-> params .transport .sdp)))
          (is (= "webrtc" (-> params .transport ._type json-value->clj))))
        (testing "service call and response"
          (is (= {:session {:id "sess_1"}
                  :transport {:sdp "answer"}}
                 (live-create client request)))
          (is (= "offer" (-> ^LiveCreateParams @captured .transport .sdp)))))
      (is false "openai.live/live-create is not implemented"))))

(deftest accepts-live-session
  (let [build-params (ns-resolve 'openai.live '->session-accept-params)
        session-accept (ns-resolve 'openai.live 'session-accept)]
    (if (and build-params session-accept)
      (let [session {:model :gpt-live-1
                     :instructions "Answer the call."
                     :audio {:output {:voice "alloy"}}}
            ^SessionAcceptParams params (build-params "sess_1" session)
            captured (atom nil)
            sessions (proxy [SessionService] []
                       (accept [p] (reset! captured p) nil))
            service (proxy [LiveService] []
                      (sessions [] sessions))
            client (proxy [OpenAIClient] []
                     (live [] service))]
        (testing "request parameters"
          (is (= "sess_1" (.get (.sessionId params))))
          (is (= "gpt-live-1" (-> params .session .model .asString)))
          (is (= "live" (-> params .session ._type json-value->clj)))
          (is (= "Answer the call."
                 (-> params .session ._additionalProperties
                     (get "instructions") json-value->clj)))
          (is (= {:output {:voice "alloy"}}
                 (-> params .session ._additionalProperties
                     (get "audio") json-value->clj))))
        (testing "service call"
          (is (nil? (session-accept client "sess_1" session)))
          (is (= "sess_1" (-> ^SessionAcceptParams @captured .sessionId .get))))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :session-id}
                 (error-data #(session-accept client nil session))))
          (is (= {:openai/error :missing-key :key :session}
                 (error-data #(session-accept client "sess_1" nil))))
          (is (= {:openai/error :missing-key :key :model}
                 (error-data #(session-accept client "sess_1" {}))))))
      (is false "openai.live/session-accept is not implemented"))))

(deftest hangs-up-live-session
  (let [build-params (ns-resolve 'openai.live '->session-hangup-params)
        session-hangup (ns-resolve 'openai.live 'session-hangup)]
    (if (and build-params session-hangup)
      (let [^SessionHangupParams params (build-params "sess_1")
            captured (atom nil)
            sessions (proxy [SessionService] []
                       (hangup [p] (reset! captured p) nil))
            service (proxy [LiveService] []
                      (sessions [] sessions))
            client (proxy [OpenAIClient] []
                     (live [] service))]
        (is (= "sess_1" (.get (.sessionId params))))
        (is (nil? (session-hangup client "sess_1")))
        (is (= "sess_1" (-> ^SessionHangupParams @captured .sessionId .get)))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(session-hangup client nil)))))
      (is false "openai.live/session-hangup is not implemented"))))

(deftest refers-live-session
  (let [build-params (ns-resolve 'openai.live '->session-refer-params)
        session-refer (ns-resolve 'openai.live 'session-refer)]
    (if (and build-params session-refer)
      (let [^SessionReferParams params
            (build-params "sess_1" "tel:+15551234567")
            captured (atom nil)
            sessions (proxy [SessionService] []
                       (refer [p] (reset! captured p) nil))
            service (proxy [LiveService] []
                      (sessions [] sessions))
            client (proxy [OpenAIClient] []
                     (live [] service))]
        (is (= "sess_1" (.get (.sessionId params))))
        (is (= "tel:+15551234567" (.targetUri params)))
        (is (nil? (session-refer client "sess_1" "tel:+15551234567")))
        (is (= "tel:+15551234567" (.targetUri ^SessionReferParams @captured)))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(session-refer client nil "tel:+15551234567"))))
        (is (= {:openai/error :missing-key :key :target-uri}
               (error-data #(session-refer client "sess_1" nil)))))
      (is false "openai.live/session-refer is not implemented"))))

(deftest rejects-live-session
  (let [build-params (ns-resolve 'openai.live '->session-reject-params)
        session-reject (ns-resolve 'openai.live 'session-reject)]
    (if (and build-params session-reject)
      (let [^SessionRejectParams params (build-params "sess_1" 486)
            captured (atom nil)
            sessions (proxy [SessionService] []
                       (reject [p] (reset! captured p) nil))
            service (proxy [LiveService] []
                      (sessions [] sessions))
            client (proxy [OpenAIClient] []
                     (live [] service))]
        (is (= "sess_1" (.get (.sessionId params))))
        (is (= 486 (.statusCode params)))
        (is (nil? (session-reject client "sess_1" 486)))
        (is (= 486 (.statusCode ^SessionRejectParams @captured)))
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(session-reject client nil 486))))
        (is (= {:openai/error :missing-key :key :status-code}
               (error-data #(session-reject client "sess_1" nil)))))
      (is false "openai.live/session-reject is not implemented"))))

(deftest forks-live-session
  (let [build-params (ns-resolve 'openai.live '->session-fork-params)
        session-fork (ns-resolve 'openai.live 'session-fork)]
    (if (and build-params session-fork)
      (let [request {:transport {:sdp "fork-offer"}
                     :session {:store true
                               :client {:data-channel {:type :webrtc}}}}
            ^SessionForkParams params (build-params "sess_1" request)
            inherited ^SessionForkParams
            (build-params "sess_1" {:transport {:sdp "fork-offer"}})
            response (-> (SessionForkResponse/builder)
                         (.session (-> (SessionForkResponse$Session/builder)
                                       (.id "sess_2")
                                       (.build)))
                         (.transport (-> (SessionForkResponse$Transport/builder)
                                         (.sdp "fork-answer")
                                         (.build)))
                         (.build))
            captured (atom nil)
            sessions (proxy [SessionService] []
                       (fork [p] (reset! captured p) response))
            service (proxy [LiveService] []
                      (sessions [] sessions))
            client (proxy [OpenAIClient] []
                     (live [] service))]
        (testing "request parameters"
          (is (= "sess_1" (.get (.sessionId params))))
          (is (= "fork-offer" (-> params .transport .sdp)))
          (is (= "webrtc" (-> params .transport ._type json-value->clj)))
          (is (= true (-> ^MediaSessionForkConfig (.get (.session params))
                          ._additionalProperties (get "store") json-value->clj)))
          (is (= {:data_channel {:type "webrtc"}}
                 (-> ^MediaSessionForkConfig (.get (.session params))
                     ._additionalProperties
                     (get "client") json-value->clj)))
          (is (not (.isPresent (.session inherited)))))
        (testing "service call and response"
          (is (= {:session {:id "sess_2"}
                  :transport {:sdp "fork-answer"}}
                 (session-fork client "sess_1" request)))
          (is (= "fork-offer" (-> ^SessionForkParams @captured .transport .sdp))))
        (testing "required fields"
          (is (= {:openai/error :missing-key :key :session-id}
                 (error-data #(session-fork client nil request))))
          (is (= {:openai/error :missing-key :key :transport}
                 (error-data #(session-fork client "sess_1" {}))))
          (is (= {:openai/error :missing-key :key :sdp}
                 (error-data #(session-fork client "sess_1"
                                            {:transport {}}))))))
      (is false "openai.live/session-fork is not implemented"))))

(deftest downloads-live-session-recording
  (let [build-params (ns-resolve 'openai.live '->session-download-recording-params)
        download (ns-resolve 'openai.live 'session-download-recording)]
    (if (and build-params download)
      (let [^SessionDownloadRecordingParams params (build-params "sess_1")
            captured (atom nil)
            closed? (atom false)
            response (proxy [com.openai.core.http.HttpResponse] []
                       (statusCode [] 200)
                       (headers [] nil)
                       (body [] (ByteArrayInputStream. (byte-array [1 2 3 4])))
                       (close [] (reset! closed? true)))
            sessions (proxy [SessionService] []
                       (downloadRecording [p] (reset! captured p) response))
            service (proxy [LiveService] []
                      (sessions [] sessions))
            client (proxy [OpenAIClient] []
                     (live [] service))]
        (is (= "sess_1" (.get (.sessionId params))))
        (is (= [1 2 3 4] (vec (download client "sess_1"))))
        (is (= "sess_1"
               (-> ^SessionDownloadRecordingParams @captured .sessionId .get)))
        (is @closed?)
        (is (= {:openai/error :missing-key :key :session-id}
               (error-data #(download client nil)))))
      (is false "openai.live/session-download-recording is not implemented"))))
