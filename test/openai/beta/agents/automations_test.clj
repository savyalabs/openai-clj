(ns openai.beta.agents.automations-test
  (:refer-clojure :exclude [list update])
  (:require [clojure.test :refer [deftest is]]
            [openai.beta.agents.automations :as automations]
            [jsonista.core :as json])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.automations Automation AutomationCreatedBy
                                                       AutomationDeleted AutomationListPage
                                                       AutomationListPageResponse AutomationListParams
                                                       AutomationTarget$ExistingSession OneTimeAutomation
                                                       OneTimeAutomation$Metadata OneTimeAutomation$PauseReason
                                                       OneTimeAutomation$Status
                                                       PeriodicAutomation PeriodicAutomation$Metadata
                                                       PeriodicAutomation$PauseReason PeriodicAutomation$Status)
           (com.openai.models.beta.agents.automations.invocations AutomationInvocation
                                                                   AutomationInvocation$Error
                                                                   AutomationInvocation$SkipReason
                                                                   AutomationInvocation$Status
                                                                   AutomationInvocationSource
                                                                   InvocationListPage InvocationListPageResponse
                                                                   InvocationListParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents AutomationService)
           (com.openai.services.blocking.beta.agents.automations InvocationService)))

(set! *warn-on-reflection* false)

(def mapper (json/object-mapper {:decode-key-fn true}))

(def periodic-request
  {:type :periodic :expression "0 9 * * *" :name "daily" :timezone "UTC"
   :target {:type :existing-session :session-id "sess_1" :instructions "Run."}
   :metadata {:team "research"}})

(defn- json-value->clj [x]
  (json/read-value (json/write-value-as-string x) mapper))

(defn- error-data [f]
  (try (f) nil (catch Throwable e (ex-data e))))

(defn- existing-session []
  (-> (AutomationTarget$ExistingSession/builder)
      (.instructions "Run.") (.sessionId "sess_1")
      (.type (JsonValue/from "existing_session")) (.build)))

(defn- periodic-automation [id]
  (let [metadata (-> (PeriodicAutomation$Metadata/builder)
                     (.putAdditionalProperty "team" (JsonValue/from "research")) .build)]
    (Automation/ofPeriodic
     (-> (PeriodicAutomation/builder)
         (.id id) (.createdAt 1700000000) (.createdBy (AutomationCreatedBy/ofDeveloper))
         (.expression "0 9 * * *") (.metadata metadata) (.name "daily")
         (.object_ (JsonValue/from "automation")) (.status PeriodicAutomation$Status/ACTIVE)
         (.pauseReason PeriodicAutomation$PauseReason/TARGET_SESSION_FAILED)
         (.target (existing-session)) (.timezone "UTC") (.type (JsonValue/from "periodic")) .build))))

(defn- once-automation [id]
  (let [metadata (-> (OneTimeAutomation$Metadata/builder)
                     (.putAdditionalProperty "team" (JsonValue/from "research")) .build)]
    (Automation/ofOnce
     (-> (OneTimeAutomation/builder)
         (.id id) (.createdAt 1700000001) (.createdBy (AutomationCreatedBy/ofDeveloper))
         (.metadata metadata) (.name "once") (.object_ (JsonValue/from "automation"))
         (.runAt 1700003600) (.status OneTimeAutomation$Status/COMPLETED)
         (.pauseReason OneTimeAutomation$PauseReason/TARGET_SESSION_FAILED)
         (.target (existing-session)) (.type (JsonValue/from "once")) .build))))

(defn- invocation [id]
  (-> (AutomationInvocation/builder)
      (.id id) (.automationId "auto_1") (.createdAt 1700000002)
      (.error (-> (AutomationInvocation$Error/builder)
                  (.code "failed") (.message "Run failed") .build))
      (.object_ (JsonValue/from "automation.invocation")) (.scheduledAt 1700003600)
      (.sessionId "sess_1") (.skipReason AutomationInvocation$SkipReason/PENDING_DELIVERY)
      (.source (AutomationInvocationSource/ofManual)) (.status AutomationInvocation$Status/SKIPPED) .build))

(defn- deleted-automation []
  (-> (AutomationDeleted/builder) (.id "auto_1") (.deleted true)
      (.object_ (JsonValue/from "automation.deleted")) .build))

(def periodic-map
  {:id "auto_1" :created-at 1700000000 :created-by {:type :developer}
   :expression "0 9 * * *" :metadata {:team "research"} :name "daily"
   :object "automation" :pause-reason :target-session-failed :status :active
   :target {:instructions "Run." :session-id "sess_1" :type :existing-session}
   :timezone "UTC" :type :periodic})

(def once-map
  {:id "auto_2" :created-at 1700000001 :created-by {:type :developer}
   :metadata {:team "research"} :name "once" :object "automation"
   :pause-reason :target-session-failed :run-at 1700003600 :status :completed
   :target {:instructions "Run." :session-id "sess_1" :type :existing-session}
   :type :once})

(def invocation-map
  {:id "inv_1" :automation-id "auto_1" :created-at 1700000002
   :error {:code "failed" :message "Run failed"}
   :object "automation.invocation" :scheduled-at 1700003600 :session-id "sess_1"
   :skip-reason :pending-delivery :source :manual :status :skipped})

(defn- client-for [^AutomationService automation-service]
  (let [agents (proxy [AgentService] [] (automations [] automation-service))
        beta (proxy [BetaService] [] (agents [] agents))]
    (proxy [OpenAIClient] [] (beta [] beta))))

(deftest automation-create-builds-schedule-unions
  (let [periodic (.automationScheduleParam (#'automations/->create-params periodic-request))
        once (.automationScheduleParam (#'automations/->create-params
                                        {:type :once :name "once" :run-at 42
                                         :target {:type :existing-session :session-id "sess_1" :instructions "Run."}}))]
    (is (.isPeriodic periodic))
    (is (= "0 9 * * *" (.expression (.asPeriodic periodic))))
    (is (= "sess_1" (.sessionId (.asExistingSession (.target (.asPeriodic periodic))))))
    (is (.isOnce once))
    (is (= 42 (.runAt (.asOnce once))))))

(deftest automation-operations-build-params
  (let [update (#'automations/->update-params "auto_1" {:expression "0 * * * *" :run-at 9})
        listed (#'automations/->list-params {:after "auto_0" :limit 3 :order :asc})
        invocation (#'automations/->invocation-list-params "auto_1" {:after "inv_0" :limit 2 :order :desc})]
    (is (= "auto_1" (.get (.automationId (#'automations/->retrieve-params "auto_1")))))
    (is (= "auto_1" (.get (.automationId (#'automations/->delete-params "auto_1")))))
    (is (= "auto_1" (.get (.automationId (#'automations/->invocation-create-params "auto_1" {:note "go"})))))
    (is (= "inv_1" (.get (.invocationId (#'automations/->invocation-retrieve-params "auto_1" "inv_1")))))
    (is (= "0 * * * *" (.get (.expression update)))) (is (= 9 (.get (.runAt update))))
    (is (= "auto_0" (.get (.after listed)))) (is (= 3 (.get (.limit listed))))
    (is (= "inv_0" (.get (.after invocation)))) (is (= 2 (.get (.limit invocation))))))

(deftest automation-required-arguments-throw-library-errors
  (doseq [[f key] [[#(#'automations/->create-params {}) :type]
                   [#(#'automations/->retrieve-params nil) :automation-id]
                   [#(#'automations/->update-params nil {}) :automation-id]
                   [#(#'automations/->delete-params nil) :automation-id]
                   [#(#'automations/->invocation-create-params nil {}) :automation-id]
                   [#(#'automations/->invocation-retrieve-params "auto_1" nil) :invocation-id]
                   [#(#'automations/->invocation-list-params nil {}) :automation-id]]]
    (is (= {:openai/error :missing-key :key key} (error-data f)))))

(deftest create
  (let [captured (atom nil) service (proxy [AutomationService] []
                                     (create [p] (reset! captured p) (periodic-automation "auto_1")))]
    (is (= periodic-map (automations/create (client-for service) periodic-request)))
    (let [schedule (.automationScheduleParam @captured)]
      (is (= "0 9 * * *" (.expression (.asPeriodic schedule))))
      (is (= "sess_1" (.sessionId (.asExistingSession (.target (.asPeriodic schedule)))))))))

(deftest retrieve
  (let [captured (atom nil) service (proxy [AutomationService] []
                                     (retrieve [p] (reset! captured p) (periodic-automation "auto_1")))]
    (is (= periodic-map (automations/retrieve (client-for service) "auto_1")))
    (is (= "auto_1" (.get (.automationId @captured))))))

(deftest update
  (let [captured (atom nil) service (proxy [AutomationService] []
                                     (update [p] (reset! captured p) (periodic-automation "auto_1")))]
    (is (= periodic-map (automations/update (client-for service) "auto_1"
                                            {:expression "0 * * * *" :metadata {:team "platform"}
                                             :name "hourly" :run-at 1700003600
                                             :target {:type :existing-session :session-id "sess_2" :instructions "Run again."}
                                             :timezone "Pacific/Honolulu"})))
    (is (= "auto_1" (.get (.automationId @captured))))
    (is (= "0 * * * *" (.get (.expression @captured))))
    (is (= {:team "platform"} (json-value->clj (.get (.metadata @captured)))))
    (is (= "hourly" (.get (.name @captured)))) (is (= 1700003600 (.get (.runAt @captured))))
    (is (= "sess_2" (.sessionId (.asExistingSession (.get (.target @captured))))))
    (is (= "Pacific/Honolulu" (.get (.timezone @captured))))))

(deftest list
  (let [captured (atom []) calls (atom 0) first-page (atom nil) second-page (atom nil)
        service (proxy [AutomationService] []
                  (list [p] (swap! captured conj p)
                        (if (= 1 (swap! calls inc)) @first-page @second-page)))
        first-response (-> (AutomationListPageResponse/builder) (.data [(periodic-automation "auto_1")])
                           (.firstId "auto_1") (.hasMore true) (.lastId "auto_1")
                           (.object_ (JsonValue/from "list")) .build)
        second-response (-> (AutomationListPageResponse/builder) (.data [(once-automation "auto_2")])
                            (.firstId "auto_2") (.hasMore false) (.lastId "auto_2")
                            (.object_ (JsonValue/from "list")) .build)]
    (reset! first-page (-> (AutomationListPage/builder) (.service service)
                           (.params (AutomationListParams/none)) (.response first-response) .build))
    (reset! second-page (-> (AutomationListPage/builder) (.service service)
                            (.params (AutomationListParams/none)) (.response second-response) .build))
    (is (= [periodic-map once-map]
           (automations/list (client-for service) {:after "auto_0" :limit 2 :order :desc})))
    (is (= 2 (count @captured)))
    (let [params (first @captured)]
      (is (= "auto_0" (.get (.after params)))) (is (= 2 (.get (.limit params))))
      (is (= "desc" (-> params .order .get .toString))))))

(deftest delete
  (let [captured (atom nil) service (proxy [AutomationService] []
                                     (delete [p] (reset! captured p) (deleted-automation)))]
    (is (= {:id "auto_1" :deleted true :object "automation.deleted"}
           (automations/delete (client-for service) "auto_1")))
    (is (= "auto_1" (.get (.automationId @captured))))))

(deftest pause
  (let [captured (atom nil) service (proxy [AutomationService] []
                                     (pause [p] (reset! captured p) (periodic-automation "auto_1")))]
    (is (= periodic-map (automations/pause (client-for service) "auto_1")))
    (is (= "auto_1" (.get (.automationId @captured))))))

(deftest resume
  (let [captured (atom nil) service (proxy [AutomationService] []
                                     (resume [p] (reset! captured p) (periodic-automation "auto_1")))]
    (is (= periodic-map (automations/resume (client-for service) "auto_1")))
    (is (= "auto_1" (.get (.automationId @captured))))))

(deftest invocation-create
  (let [captured (atom nil) invocation-service (proxy [InvocationService] []
                                                 (create [p] (reset! captured p) (invocation "inv_1")))
        service (proxy [AutomationService] [] (invocations [] invocation-service))]
    (is (= invocation-map (automations/invocation-create (client-for service) "auto_1"
                                                          {:session-id "sess_1" :note "go"})))
    (is (= "auto_1" (.get (.automationId @captured))))
    (is (= {:session_id "sess_1" :note "go"}
           (json-value->clj (JsonValue/from (._additionalBodyProperties @captured)))))))

(deftest invocation-retrieve
  (let [captured (atom nil) invocation-service (proxy [InvocationService] []
                                                 (retrieve [p] (reset! captured p) (invocation "inv_1")))
        service (proxy [AutomationService] [] (invocations [] invocation-service))]
    (is (= invocation-map (automations/invocation-retrieve (client-for service) "auto_1" "inv_1")))
    (is (= "auto_1" (.automationId @captured)))
    (is (= "inv_1" (.get (.invocationId @captured))))))

(deftest invocation-list
  (let [captured (atom []) calls (atom 0) first-page (atom nil) second-page (atom nil)
        invocation-service (proxy [InvocationService] []
                             (list [p] (swap! captured conj p)
                                   (if (= 1 (swap! calls inc)) @first-page @second-page)))
        service (proxy [AutomationService] [] (invocations [] invocation-service))
        first-response (-> (InvocationListPageResponse/builder) (.data [(invocation "inv_1")])
                           (.firstId "inv_1") (.hasMore true) (.lastId "inv_1")
                           (.object_ (JsonValue/from "list")) .build)
        second-response (-> (InvocationListPageResponse/builder) (.data [(invocation "inv_2")])
                            (.firstId "inv_2") (.hasMore false) (.lastId "inv_2")
                            (.object_ (JsonValue/from "list")) .build)]
    (reset! first-page (-> (InvocationListPage/builder) (.service invocation-service)
                           (.params (InvocationListParams/none)) (.response first-response) .build))
    (reset! second-page (-> (InvocationListPage/builder) (.service invocation-service)
                            (.params (InvocationListParams/none)) (.response second-response) .build))
    (is (= [invocation-map (assoc invocation-map :id "inv_2")]
           (automations/invocation-list (client-for service) "auto_1"
                                        {:after "inv_0" :limit 2 :order :asc})))
    (is (= 2 (count @captured)))
    (let [params (first @captured)]
      (is (= "auto_1" (.get (.automationId params)))) (is (= "inv_0" (.get (.after params))))
      (is (= 2 (.get (.limit params)))) (is (= "asc" (-> params .order .get .toString))))))

(deftest automation-models-round-trip-to-kebab-case-maps
  (let [automation-service (proxy [AutomationService] []
                             (create [_] (periodic-automation "auto_1"))
                             (retrieve [_] (once-automation "auto_2"))
                             (delete [_] (deleted-automation)))
        invocation-service (proxy [InvocationService] [] (retrieve [_] (invocation "inv_1")))
        service (proxy [AutomationService] []
                  (create [p] (.create automation-service p))
                  (retrieve [p] (.retrieve automation-service p))
                  (delete [p] (.delete automation-service p))
                  (invocations [] invocation-service))]
    (is (= periodic-map (automations/create (client-for service) periodic-request)))
    (is (= once-map (automations/retrieve (client-for service) "auto_2")))
    (is (= {:id "auto_1" :deleted true :object "automation.deleted"}
           (automations/delete (client-for service) "auto_1")))
    (is (= invocation-map (automations/invocation-retrieve (client-for service) "auto_1" "inv_1")))))
