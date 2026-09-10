(ns openai.live-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [openai.live :as live])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.live LiveCreateParams
                                   LiveCreateResponse
                                   LiveCreateResponse$Session
                                   LiveCreateResponse$Transport)
           (com.openai.services.blocking LiveService)))

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
