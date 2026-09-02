(ns openai.admin-test
  (:require [clojure.test :refer [deftest is]]
            [openai.admin :as admin]
            [openai.admin.projects :as projects]
            [openai.impl :as impl])
  (:import (com.openai.models.admin.organization.adminapikeys AdminApiKeyCreateParams)
           (com.openai.models.admin.organization.auditlogs AuditLogListParams AuditLogListParams$EventType)
           (com.openai.models.admin.organization.groups Group Group$Builder GroupCreateParams)
           (com.openai.models.admin.organization.groups.users UserCreateParams)
           (com.openai.models.admin.organization.invites Invite Invite$Builder Invite$Role Invite$Status)
           (com.openai.models.admin.organization.projects Project ProjectCreateParams)
           (com.openai.models.admin.organization.projects.apikeys ApiKeyListParams ApiKeyListParams$OwnerProjectAccess ProjectApiKey ProjectApiKey$Owner ProjectApiKey$OwnerProjectAccess)
           (com.openai.models.admin.organization.projects.ratelimits ProjectRateLimit RateLimitUpdateRateLimitParams)
           (com.openai.models.admin.organization.projects.serviceaccounts ProjectServiceAccount ServiceAccountCreateParams)
           (com.openai.models.admin.organization.projects.serviceaccounts.apikeys ApiKeyCreateResponse)
           (com.openai.models.admin.organization.projects.users.roles RoleListResponse)
           (com.openai.models.admin.organization.projects.spendlimit ProjectSpendLimit ProjectSpendLimitDeleted ProjectSpendLimit$Currency ProjectSpendLimit$Interval ProjectSpendLimit$Enforcement ProjectSpendLimit$Enforcement$Status)
           (com.openai.models.admin.organization.spendlimit OrganizationSpendLimit OrganizationSpendLimitDeleted OrganizationSpendLimit$Currency OrganizationSpendLimit$Interval OrganizationSpendLimit$Enforcement OrganizationSpendLimit$Enforcement$Status)
           (com.openai.models.admin.organization.spendalerts OrganizationSpendAlert OrganizationSpendAlert$Currency OrganizationSpendAlert$Interval OrganizationSpendAlert$NotificationChannel)
           (com.openai.models.admin.organization.usage UsageCompletionsParams UsageCostsParams UsageCompletionsResponse$Data UsageCompletionsResponse$Data$Result$OrganizationUsageCompletionsResult)))
(set! *warn-on-reflection* true)
(deftest translates-project-create
  (let [^ProjectCreateParams p (#'admin/->project-create-params {:name "research"})]
    (is (= "research" (.name p)))))

(deftest converts-project-present-only
  (let [project (-> (Project/builder) (.id "proj_1") (.createdAt 123)
                    (.name "Research") (.status "active") (.build))]
    (is (= {:id "proj_1" :created-at 123 :name "Research" :status "active"}
           (#'admin/project->map project)))))

(deftest converts-spend-alert
  (let [channel (-> (OrganizationSpendAlert$NotificationChannel/builder)
                    (.recipients ["ops@example.com"]) (.type (com.openai.core.JsonValue/from "email")) (.build))
        alert (-> (OrganizationSpendAlert/builder) (.id "alert_1")
                  (.currency (OrganizationSpendAlert$Currency/of "usd"))
                  (.interval (OrganizationSpendAlert$Interval/of "monthly"))
                  (.notificationChannel channel) (.thresholdAmount 1000) (.build))]
    (is (= {:id "alert_1" :currency :usd :interval :monthly
            :notification-channel {:recipients ["ops@example.com"] :type "email"}
            :threshold-amount 1000}
           (#'admin/spend-alert->map alert)))))

(deftest converts-organization-spend-limit
  (let [enforcement (-> (OrganizationSpendLimit$Enforcement/builder)
                        (.status (OrganizationSpendLimit$Enforcement$Status/of "enforcing")) (.build))
        limit (-> (OrganizationSpendLimit/builder) (.currency (OrganizationSpendLimit$Currency/of "usd"))
                  (.enforcement enforcement) (.interval (OrganizationSpendLimit$Interval/of "month"))
                  (.thresholdAmount 2500) (.build))
        deleted (-> (OrganizationSpendLimitDeleted/builder) (.deleted true) (.build))
        f (some-> (ns-resolve 'openai.admin 'spend-limit->map) deref)
        deleted-f (some-> (ns-resolve 'openai.admin 'spend-limit-deleted->map) deref)]
    (is (= {:currency :usd :enforcement :enforcing :interval :month :threshold-amount 2500}
           (f limit)))
    (is (= {:deleted true} (deleted-f deleted)))))

(deftest builds-organization-spend-limit-update-params
  (let [f (some-> (ns-resolve 'openai.admin '->spend-limit-update-params) deref)
        ^com.openai.models.admin.organization.spendlimit.SpendLimitUpdateParams p (f {:currency :usd :interval :month :threshold-amount 2500})]
    (is (= :usd (impl/->keyword (.currency p))))
    (is (= :month (impl/->keyword (.interval p))))
    (is (= 2500 (.thresholdAmount p)))))

(deftest converts-project-spend-limit
  (let [enforcement (-> (ProjectSpendLimit$Enforcement/builder)
                        (.status (ProjectSpendLimit$Enforcement$Status/of "inactive")) (.build))
        limit (-> (ProjectSpendLimit/builder) (.currency (ProjectSpendLimit$Currency/of "usd"))
                  (.enforcement enforcement) (.interval (ProjectSpendLimit$Interval/of "month"))
                  (.thresholdAmount 3500) (.build))
        deleted (-> (ProjectSpendLimitDeleted/builder) (.deleted true) (.build))
        f (some-> (ns-resolve 'openai.admin.projects 'spend-limit->map) deref)
        deleted-f (some-> (ns-resolve 'openai.admin.projects 'spend-limit-deleted->map) deref)]
    (is (= {:currency :usd :enforcement :inactive :interval :month :threshold-amount 3500} (f limit)))
    (is (= {:deleted true} (deleted-f deleted)))))

(deftest builds-project-spend-limit-update-params
  (let [f (some-> (ns-resolve 'openai.admin.projects '->spend-limit-update-params) deref)
        ^com.openai.models.admin.organization.projects.spendlimit.SpendLimitUpdateParams p (f "proj_1" {:currency :usd :interval :month :threshold-amount 3500})]
    (is (= "proj_1" (impl/opt-get (.projectId p))))
    (is (= :usd (impl/->keyword (.currency p))))
    (is (= :month (impl/->keyword (.interval p))))
    (is (= 3500 (.thresholdAmount p)))))

(deftest builds-service-account-api-key-create-params
  (let [f (some-> (ns-resolve 'openai.admin.projects '->service-account-api-key-create-params) deref)
        ^com.openai.models.admin.organization.projects.serviceaccounts.apikeys.ApiKeyCreateParams p (f "proj_1" "svc_1" {:name "deploy" :scopes ["api.responses.write"]})]
    (is (= "proj_1" (.projectId p)))
    (is (= "svc_1" (impl/opt-get (.serviceAccountId p))))
    (is (= "deploy" (impl/opt-get (.name p))))
    (is (= ["api.responses.write"] (impl/opt-get (.scopes p))))))

(deftest converts-service-account-api-key-create-response
  (let [response (-> (ApiKeyCreateResponse/builder) (.id "key_1") (.createdAt 456)
                     (.name "deploy") (.value "sk-secret") (.build))
        f (some-> (ns-resolve 'openai.admin.projects 'service-account-api-key-create-response->map) deref)]
    (is (= {:id "key_1" :created-at 456 :name "deploy" :value "sk-secret"} (f response)))))

(deftest builds-usage-completions-params
  (let [^UsageCompletionsParams p (#'admin/->usage-completions-params
                                    {:start-time 100 :bucket-width :day
                                     :group-by [:model] :batch true})]
    (is (= 100 (.startTime p)))
    (is (= "1d" (str (impl/opt-get (.bucketWidth p)))))
    (is (= ["model"] (mapv str (impl/opt-get (.groupBy p)))))
    (is (true? (impl/opt-get (.batch p))))))

(deftest builds-usage-costs-params-with-line-items
  (let [^UsageCostsParams p (#'admin/->usage-costs-params
                             {:start-time 100 :line-items ["tokens" "storage"]})]
    (is (= ["tokens" "storage"] (impl/opt-get (.lineItems p))))))

(deftest converts-usage-completions-bucket
  (let [result (-> (UsageCompletionsResponse$Data$Result$OrganizationUsageCompletionsResult/builder)
                   (.inputTokens 10) (.numModelRequests 2) (.outputTokens 4)
                   (.model "gpt-4.1") (.build))
        bucket (-> (UsageCompletionsResponse$Data/builder) (.startTime 100) (.endTime 200)
                   (.addResult result) (.build))]
    (is (= {:start-time 100 :end-time 200
            :results [{:input-tokens 10 :num-model-requests 2 :output-tokens 4
                       :model "gpt-4.1"}]}
           (#'admin/usage-completions-bucket->map bucket)))))

(deftest exposes-organization-and-project-service-operations
  (doseq [v [#'admin/admin-api-key-create #'admin/audit-log-list
             #'admin/certificate-activate #'admin/group-user-create
             #'admin/project-archive #'admin/usage-completions
             #'projects/api-key-list #'projects/model-permission-update
             #'projects/rate-limit-update-rate-limit
             #'projects/service-account-create #'projects/user-role-delete]]
    (is (fn? @v) (str (:name (meta v)) " should be callable"))))

(deftest exposes-typed-project-admin-helpers
  (doseq [sym '[->group-create-params project-api-key->map user-role-list->map
                ->service-account-create-params service-account->map
                ->rate-limit-update-params rate-limit->map]]
    (is (fn? (some-> (ns-resolve 'openai.admin.projects sym) deref))
        (str sym " should be defined"))))

(deftest builds-project-group-create-params
  (when-let [f (some-> (ns-resolve 'openai.admin.projects '->group-create-params) deref)]
    (let [^com.openai.models.admin.organization.projects.groups.GroupCreateParams p
          (f "proj_1" {:group-id "group_1" :role "member"})]
      (is (= "proj_1" (impl/opt-get (.projectId p))))
      (is (= "group_1" (.groupId p)))
      (is (= "member" (.role p))))))

(deftest builds-project-api-key-list-params
  (when-let [f (some-> (ns-resolve 'openai.admin.projects '->api-key-list-params) deref)]
    (doseq [[option expected] [[:active ApiKeyListParams$OwnerProjectAccess/ACTIVE]
                               [:inactive ApiKeyListParams$OwnerProjectAccess/INACTIVE]
                               [:any ApiKeyListParams$OwnerProjectAccess/ANY]]]
      (let [^ApiKeyListParams p (f "proj_1" {:owner-project-access option})]
        (is (= expected (impl/opt-get (.ownerProjectAccess p))))))
    (let [^ApiKeyListParams p (f "proj_1" {})]
      (is (false? (.isPresent (.ownerProjectAccess p)))))))

(deftest converts-project-api-key-present-only
  (when-let [f (some-> (ns-resolve 'openai.admin.projects 'project-api-key->map) deref)]
    (let [empty (java.util.Optional/empty)
          missing (com.openai.core.JsonField/ofNullable nil)
          ^com.openai.models.admin.organization.projects.apikeys.ProjectApiKey$Owner$Builder ob
          (ProjectApiKey$Owner/builder)
          owner (do (.serviceAccount ob ^com.openai.core.JsonField missing)
                    (.type ob ^com.openai.core.JsonField missing)
                    (.user ob ^com.openai.core.JsonField missing)
                    (.build ob))
          ^com.openai.models.admin.organization.projects.apikeys.ProjectApiKey$Builder kb
          (ProjectApiKey/builder)
          k (do (.id kb "key_1") (.createdAt kb 123) (.lastUsedAt kb empty)
                (.name kb "Deploy") (.owner kb owner)
                (.ownerProjectAccess kb ^com.openai.core.JsonField missing)
                (.redactedValue kb "sk-...abc") (.build kb))]
      (is (= {:id "key_1" :created-at 123 :name "Deploy"
              :owner {} :redacted-value "sk-...abc"}
             (f k)))
      (let [k (-> (ProjectApiKey/builder) (.id "key_2") (.createdAt 456)
                  (.lastUsedAt ^com.openai.core.JsonField missing)
                  (.name "Deploy") (.owner owner)
                  (.ownerProjectAccess ProjectApiKey$OwnerProjectAccess/ACTIVE)
                  (.redactedValue "sk-...def") (.build))]
        (is (= {:id "key_2" :created-at 456 :name "Deploy" :owner {}
                :owner-project-access :active :redacted-value "sk-...def"}
               (f k)))))))

(deftest converts-project-user-role-present-only
  (when-let [f (some-> (ns-resolve 'openai.admin.projects 'user-role-list->map) deref)]
    (let [empty (java.util.Optional/empty)
          r (-> (RoleListResponse/builder) (.id "role_1")
                (.assignmentSources empty) (.createdAt empty) (.createdBy empty)
                (.createdByUserObj empty) (.description empty) (.metadata empty)
                (.name "Reader")
                (.permissions ["project.read"]) (.predefinedRole true)
                (.resourceType "project") (.updatedAt empty) (.build))]
      (is (= {:id "role_1" :name "Reader" :permissions ["project.read"]
              :predefined-role true :resource-type "project"}
             (f r))))))

(deftest builds-project-service-account-create-params
  (when-let [f (some-> (ns-resolve 'openai.admin.projects '->service-account-create-params) deref)]
    (let [^ServiceAccountCreateParams p (f "proj_1" {:name "deploy"})]
      (is (= "proj_1" (impl/opt-get (.projectId p))))
      (is (= "deploy" (.name p))))))

(deftest converts-project-service-account
  (when-let [f (some-> (ns-resolve 'openai.admin.projects 'service-account->map) deref)]
    (let [a (-> (ProjectServiceAccount/builder) (.id "svc_1") (.createdAt 123)
                (.name "Deploy") (.role (com.openai.models.admin.organization.projects.serviceaccounts.ProjectServiceAccount$Role/of "member"))
                (.build))]
      (is (= {:id "svc_1" :created-at 123 :name "Deploy" :role :member} (f a))))))

(deftest builds-project-rate-limit-update-params
  (when-let [f (some-> (ns-resolve 'openai.admin.projects '->rate-limit-update-params) deref)]
    (let [^RateLimitUpdateRateLimitParams p
          (f "proj_1" "rl_1" {:max-requests-per-1-minute 100})]
      (is (= "proj_1" (.projectId p)))
      (is (= "rl_1" (impl/opt-get (.rateLimitId p))))
      (is (= 100 (impl/opt-get (.maxRequestsPer1Minute p)))))))

(deftest converts-project-rate-limit-present-only
  (when-let [f (some-> (ns-resolve 'openai.admin.projects 'rate-limit->map) deref)]
    (let [r (-> (ProjectRateLimit/builder) (.id "rl_1") (.maxRequestsPer1Minute 100)
                (.maxTokensPer1Minute 2000) (.model "gpt-4.1") (.build))]
      (is (= {:id "rl_1" :max-requests-per-1-minute 100
              :max-tokens-per-1-minute 2000 :model "gpt-4.1"}
             (f r))))))

(deftest builds-admin-api-key-create-params
  (let [^AdminApiKeyCreateParams p (#'admin/->admin-api-key-create-params
                                     {:name "deploy" :expires-in-seconds 3600})]
    (is (= "deploy" (.name p)))
    (is (= 3600 (impl/opt-get (.expiresInSeconds p))))))

(deftest builds-audit-log-list-params-with-new-event-type
  (let [^AuditLogListParams p (#'admin/->audit-log-list-params
                                {:event-types [:tenant-workload-identity-access-token-issued]})]
    (is (= ["tenant_workload_identity_access_token_issued"]
           (mapv #(.asString ^AuditLogListParams$EventType %) (impl/opt-get (.eventTypes p)))))))

(deftest converts-invite-present-only
  (let [^Invite$Builder b (Invite/builder)
        invite (do (.id b "invite_1") (.createdAt b 123) (.email b "dev@example.com")
                   (.projects b (java.util.ArrayList.)) (.role b (Invite$Role/of "reader"))
                   (.status b (Invite$Status/of "pending")) (.build b))]
    (is (= {:id "invite_1" :created-at 123 :email "dev@example.com"
            :projects [] :role :reader :status :pending}
           (#'admin/invite->map invite)))))

(deftest builds-group-create-params
  (let [^GroupCreateParams p (#'admin/->group-create-params {:name "platform"})]
    (is (= "platform" (.name p)))))

(deftest converts-group
  (let [^Group$Builder b (Group/builder)
        group (-> b (.id "group_1") (.createdAt 123)
                  (.groupType (com.openai.models.admin.organization.groups.Group$GroupType/of "custom"))
                  (.isScimManaged false) (.name "Platform") (.build))]
    (is (= {:id "group_1" :created-at 123 :group-type :custom
            :is-scim-managed false :name "Platform"}
           (#'admin/group->map group)))))

(deftest builds-group-user-create-params
  (let [^UserCreateParams p (#'admin/->group-user-create-params
                              "group_1" {:user-id "user_1"})]
    (is (= "group_1" (impl/opt-get (.groupId p))))
    (is (= "user_1" (.userId p)))))
