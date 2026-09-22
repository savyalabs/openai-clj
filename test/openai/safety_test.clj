(ns openai.safety-test
  (:require [clojure.test :refer [deftest is]]
            [openai.impl :as impl]
            [openai.safety :as safety])
  (:import (com.openai.models.safety.alerts AlertRetrieveParams
                                            SafetyAlert
                                            SafetyAlert$ErrorType)
           (com.openai.models.safety.cases CaseRetrieveParams SafetyCase)))

(set! *warn-on-reflection* true)

(deftest converts-safety-alert-with-reason
  (let [alert (-> (SafetyAlert/builder)
                  (.id "alert_123")
                  (.createdAt 123456789)
                  (.errorType (SafetyAlert$ErrorType/of "potentially_unintended_data_access"))
                  (.model "gpt-5")
                  (.reason "The request was paused for review.")
                  (.requestId "req_1")
                  (.requestPaused true)
                  (.responseId "resp_1")
                  (.build))]
    (is (= {:id "alert_123"
            :created-at 123456789
            :error-type :potentially-unintended-data-access
            :model "gpt-5"
            :request-id "req_1"
            :request-paused true
            :response-id "resp_1"
            :reason "The request was paused for review."}
           (#'safety/alert->map alert)))))

(deftest omits-absent-safety-alert-reason
  (let [alert (-> (SafetyAlert/builder)
                  (.id "alert_123")
                  (.createdAt 123456789)
                  (.errorType SafetyAlert$ErrorType/OTHER)
                  (.model "gpt-5")
                  (.reason (java.util.Optional/empty))
                  (.requestId "req_1")
                  (.requestPaused false)
                  (.responseId "resp_1")
                  (.build))
        mapped (#'safety/alert->map alert)]
    (is (not (contains? mapped :reason)))
    (is (= :other (:error-type mapped)))))

(deftest builds-alert-retrieve-params
  (let [^AlertRetrieveParams params (#'safety/->retrieve-params "alert_123")]
    (is (= "alert_123" (.get (.id params))))))

(deftest exposes-safety-case-retrieve
  (is (fn? (some-> (ns-resolve 'openai.safety 'case-retrieve) deref))
      "missing case-retrieve wrapper"))

(deftest builds-and-converts-safety-case
  (let [^CaseRetrieveParams params (#'safety/->case-retrieve-params "case_123")
        safety-case (impl/sdk-input-object
                     {:id "case_123" :created-at 123 :entity-identifier "org_123"
                      :notice {:type "warning"} :reason "Review required"}
                     SafetyCase)]
    (is (= "case_123" (.get (.id params))))
    (is (= {:id "case_123" :created-at 123 :entity-identifier "org_123"
            :notice {:type :warning} :reason "Review required"}
           (#'safety/safety-case->map safety-case)))))
