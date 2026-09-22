(ns openai.safety
  "Clojure wrapper for the OpenAI Safety API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.safety.alerts AlertRetrieveParams
                                            SafetyAlert
                                            SafetyAlert$ErrorType)
           (com.openai.models.safety.cases CaseRetrieveParams
                                           SafetyCase
                                           SafetyCase$Notice$Type)
           (com.openai.services.blocking SafetyService)
           (com.openai.services.blocking.safety AlertService CaseService)))
(set! *warn-on-reflection* true)

(defn- ->retrieve-params ^AlertRetrieveParams [^String alert-id]
  (-> (AlertRetrieveParams/builder) (.id alert-id) (.build)))

(defn- alert->map [^SafetyAlert alert]
  (cond-> {:id (.id alert)
           :created-at (.createdAt alert)
           :error-type (impl/->keyword (.asString ^SafetyAlert$ErrorType (.errorType alert)))
           :model (.model alert)
           :request-id (.requestId alert)
           :request-paused (.requestPaused alert)
           :response-id (.responseId alert)}
    (.isPresent (.reason alert)) (assoc :reason (impl/opt-get (.reason alert)))))

(defn retrieve [^OpenAIClient client ^String alert-id]
  (impl/with-api-errors
    (let [^SafetyService svc (.safety client)
          ^AlertService alerts (.alerts svc)]
      (alert->map (.retrieve alerts (->retrieve-params alert-id))))))

(defn- ->case-retrieve-params ^CaseRetrieveParams [^String case-id]
  (when-not case-id (impl/missing-key! :case-id))
  (-> (CaseRetrieveParams/builder) (.id case-id) (.build)))

(defn- safety-case->map [^SafetyCase safety-case]
  (cond-> {:id (.id safety-case)
           :created-at (.createdAt safety-case)
           :entity-identifier (.entityIdentifier safety-case)
           :notice {:type (impl/->keyword
                           (.asString ^SafetyCase$Notice$Type
                                      (.type (.notice safety-case))))}}
    (.isPresent (.reason safety-case))
    (assoc :reason (impl/opt-get (.reason safety-case)))))

(defn case-retrieve [^OpenAIClient client ^String case-id]
  (impl/with-api-errors
    (let [^SafetyService svc (.safety client)
          ^CaseService cases (.cases svc)]
      (safety-case->map (.retrieve cases (->case-retrieve-params case-id))))))
