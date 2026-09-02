(ns openai.admin
  "OpenAI Admin API wrappers for an organization."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.admin.organization.adminapikeys AdminApiKey AdminApiKey$Owner AdminApiKeyCreateParams AdminApiKeyCreateResponse AdminApiKeyDeleteResponse AdminApiKeyListPage AdminApiKeyListParams AdminApiKeyListParams$Order)
           (com.openai.models.admin.organization.auditlogs AuditLogListPage AuditLogListParams AuditLogListParams$EffectiveAt AuditLogListParams$EventType AuditLogListResponse)
           (com.openai.models.admin.organization.certificates Certificate Certificate$CertificateDetails CertificateActivatePage CertificateActivateParams CertificateActivateResponse CertificateActivateResponse$CertificateDetails CertificateCreateParams CertificateDeactivatePage CertificateDeactivateParams CertificateDeactivateResponse CertificateDeactivateResponse$CertificateDetails CertificateDeleteResponse CertificateListPage CertificateListParams CertificateListParams$Order CertificateListResponse CertificateListResponse$CertificateDetails CertificateRetrieveParams CertificateRetrieveParams$Include CertificateUpdateParams)
           (com.openai.models.admin.organization.dataretention DataRetentionUpdateParams DataRetentionUpdateParams$RetentionType OrganizationDataRetention)
           (com.openai.models.admin.organization.groups Group GroupCreateParams GroupDeleteResponse GroupListPage GroupListParams GroupListParams$Order GroupUpdateParams GroupUpdateResponse)
           (com.openai.models.admin.organization.invites Invite Invite$Project InviteCreateParams InviteCreateParams$Project InviteCreateParams$Project$Role InviteCreateParams$Role InviteDeleteResponse InviteListPage InviteListParams)
           (com.openai.models.admin.organization.projects Project ProjectCreateParams ProjectListPage ProjectListParams ProjectUpdateParams)
           (com.openai.models.admin.organization.spendlimit OrganizationSpendLimit OrganizationSpendLimitDeleted SpendLimitUpdateParams SpendLimitUpdateParams$Currency SpendLimitUpdateParams$Interval)
           (com.openai.models.admin.organization.spendalerts OrganizationSpendAlert OrganizationSpendAlertDeleted OrganizationSpendAlert$NotificationChannel SpendAlertCreateParams SpendAlertCreateParams$Currency SpendAlertCreateParams$Interval SpendAlertCreateParams$NotificationChannel SpendAlertListPage SpendAlertListParams SpendAlertListParams$Order SpendAlertUpdateParams SpendAlertUpdateParams$Currency SpendAlertUpdateParams$Interval SpendAlertUpdateParams$NotificationChannel)
           (com.openai.models.admin.organization.roles Role RoleCreateParams RoleDeleteResponse RoleListPage RoleListParams RoleListParams$Order RoleUpdateParams)
           (com.openai.models.admin.organization.users OrganizationUser UserDeleteResponse UserListPage UserListParams UserUpdateParams)
           (com.openai.services.blocking.admin OrganizationService)
           (com.openai.services.blocking.admin.organization AdminApiKeyService AuditLogService CertificateService DataRetentionService GroupService InviteService ProjectService RoleService SpendAlertService SpendLimitService UsageService UserService)))
(set! *warn-on-reflection* true)

(defn- ->project-create-params ^ProjectCreateParams [{:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (ProjectCreateParams/builder) (.name ^String name) (.build)))

(defn- organization ^OrganizationService [^OpenAIClient client]
  (.organization (.admin client)))

(defn- ->admin-api-key-create-params ^AdminApiKeyCreateParams [{:keys [name expires-in-seconds]}]
  (when-not name (impl/missing-key! :name))
  (let [b (AdminApiKeyCreateParams/builder)]
    (.name b ^String name)
    (when expires-in-seconds (.expiresInSeconds b (long expires-in-seconds)))
    (.build b)))

(defn- ->admin-api-key-list-params ^AdminApiKeyListParams [{:keys [after limit order]}]
  (let [b (AdminApiKeyListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (AdminApiKeyListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- admin-api-key-owner->map [^AdminApiKey$Owner o]
  (cond-> {}
    (.isPresent (.id o)) (assoc :id (impl/opt-get (.id o)))
    (.isPresent (.createdAt o)) (assoc :created-at (impl/opt-get (.createdAt o)))
    (.isPresent (.name o)) (assoc :name (impl/opt-get (.name o)))
    (.isPresent (.role o)) (assoc :role (impl/opt-get (.role o)))
    (.isPresent (.type o)) (assoc :type (impl/opt-get (.type o)))))
(defn- admin-api-key->map [^AdminApiKey k]
  (cond-> {:id (.id k) :created-at (.createdAt k) :owner (admin-api-key-owner->map (.owner k))
           :redacted-value (.redactedValue k)}
    (.isPresent (.expiresAt k)) (assoc :expires-at (impl/opt-get (.expiresAt k)))
    (.isPresent (.lastUsedAt k)) (assoc :last-used-at (impl/opt-get (.lastUsedAt k)))
    (.isPresent (.name k)) (assoc :name (impl/opt-get (.name k)))))

(defn- admin-api-key-create-response->map [^AdminApiKeyCreateResponse k]
  (assoc (admin-api-key->map (.toAdminApiKey k)) :value (.value k)))

(defn admin-api-key-create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^AdminApiKeyService svc (.adminApiKeys (organization client))]
      (admin-api-key-create-response->map (.create svc (->admin-api-key-create-params req))))))
(defn admin-api-key-retrieve [^OpenAIClient client ^String key-id]
  (impl/with-api-errors
    (let [^AdminApiKeyService svc (.adminApiKeys (organization client))]
      (admin-api-key->map (.retrieve svc key-id)))))
(defn admin-api-key-list
  ([^OpenAIClient client] (admin-api-key-list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^AdminApiKeyService svc (.adminApiKeys (organization client))
           ^AdminApiKeyListPage page (.list svc (->admin-api-key-list-params opts))]
       (mapv admin-api-key->map (impl/all-pages page))))))
(defn admin-api-key-delete [^OpenAIClient client ^String key-id]
  (impl/with-api-errors
    (let [^AdminApiKeyService svc (.adminApiKeys (organization client))
          ^AdminApiKeyDeleteResponse r (.delete svc key-id)]
      {:id (.id r) :deleted (.deleted r)})))

(defn- ->effective-at ^AuditLogListParams$EffectiveAt [{:keys [gt gte lt lte]}]
  (let [b (AuditLogListParams$EffectiveAt/builder)]
    (when gt (.gt b (long gt))) (when gte (.gte b (long gte)))
    (when lt (.lt b (long lt))) (when lte (.lte b (long lte))) (.build b)))
(defn- ->audit-log-list-params ^AuditLogListParams [{:keys [effective-at project-ids event-types actor-ids actor-emails resource-ids limit after before]}]
  (let [b (AuditLogListParams/builder)]
    (when effective-at (.effectiveAt b (->effective-at effective-at)))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when event-types (.eventTypes b ^java.util.List (mapv #(AuditLogListParams$EventType/of (impl/enum-name %)) event-types)))
    (when actor-ids (.actorIds b ^java.util.List actor-ids))
    (when actor-emails (.actorEmails b ^java.util.List actor-emails))
    (when resource-ids (.resourceIds b ^java.util.List resource-ids))
    (when limit (.limit b (long limit))) (when after (.after b ^String after))
    (when before (.before b ^String before)) (.build b)))
(defn- audit-log->map [^AuditLogListResponse a]
  {:id (.id a) :effective-at (.effectiveAt a) :type (impl/->keyword (.type a))})
(defn audit-log-list
  ([^OpenAIClient client] (audit-log-list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^AuditLogService svc (.auditLogs (organization client))
           ^AuditLogListPage page (.list svc (->audit-log-list-params opts))]
       (mapv audit-log->map (impl/all-pages page))))))

(defn- ->certificate-create-params ^CertificateCreateParams [{:keys [certificate name]}]
  (when-not certificate (impl/missing-key! :certificate))
  (let [b (CertificateCreateParams/builder)] (.certificate b ^String certificate)
        (when name (.name b ^String name)) (.build b)))
(defn- ->certificate-retrieve-params ^CertificateRetrieveParams [^String id {:keys [include]}]
  (let [b (CertificateRetrieveParams/builder)] (.certificateId b id)
    (when include (.include b ^java.util.List (mapv #(CertificateRetrieveParams$Include/of (impl/enum-name %)) include))) (.build b)))
(defn- ->certificate-update-params ^CertificateUpdateParams [^String id {:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (CertificateUpdateParams/builder) (.certificateId id) (.name ^String name) (.build)))
(defn- ->certificate-list-params ^CertificateListParams [{:keys [after limit order]}]
  (let [b (CertificateListParams/builder)] (when after (.after b ^String after))
    (when limit (.limit b (long limit))) (when order (.order b (CertificateListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- certificate-details->map [^Certificate$CertificateDetails d]
  (cond-> {} (.isPresent (.content d)) (assoc :content (impl/opt-get (.content d)))
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate-list-details->map [^CertificateListResponse$CertificateDetails d]
  (cond-> {} (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate-activate-details->map [^CertificateActivateResponse$CertificateDetails d]
  (cond-> {} (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate-deactivate-details->map [^CertificateDeactivateResponse$CertificateDetails d]
  (cond-> {} (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate->map [^Certificate c]
  (cond-> {:id (.id c) :certificate-details (certificate-details->map (.certificateDetails c)) :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))
    (.isPresent (.active c)) (assoc :active (impl/opt-get (.active c)))))
(defn- certificate-list->map [^CertificateListResponse c]
  (cond-> {:id (.id c) :active (.active c) :certificate-details (certificate-list-details->map (.certificateDetails c)) :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))
(defn certificate-create [^OpenAIClient client req] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client))] (certificate->map (.create s (->certificate-create-params req))))))
(defn certificate-retrieve
  ([^OpenAIClient client ^String id] (certificate-retrieve client id {}))
  ([^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client))] (certificate->map (.retrieve s (->certificate-retrieve-params id opts)))))))
(defn certificate-update [^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client))] (certificate->map (.update s (->certificate-update-params id opts))))))
(defn certificate-list
  ([^OpenAIClient client] (certificate-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateListPage p (.list s (->certificate-list-params opts))] (mapv certificate-list->map (impl/all-pages p))))))
(defn certificate-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateDeleteResponse r (.delete s id)] {:id (.id r)})))
(defn- ->certificate-activate-params ^CertificateActivateParams [^String id]
  (let [b (CertificateActivateParams/builder)
        ids (doto (java.util.ArrayList.) (.add id))]
    (.certificateIds b ids) (.build b)))
(defn- ->certificate-deactivate-params ^CertificateDeactivateParams [^String id]
  (let [b (CertificateDeactivateParams/builder)
        ids (doto (java.util.ArrayList.) (.add id))]
    (.certificateIds b ids) (.build b)))
(defn- certificate-activate->map [^CertificateActivateResponse c] (cond-> {:id (.id c) :active (.active c) :certificate-details (certificate-activate-details->map (.certificateDetails c)) :created-at (.createdAt c)} (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))
(defn- certificate-deactivate->map [^CertificateDeactivateResponse c] (cond-> {:id (.id c) :active (.active c) :certificate-details (certificate-deactivate-details->map (.certificateDetails c)) :created-at (.createdAt c)} (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))
(defn certificate-activate [^OpenAIClient client ^String id] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateActivatePage p (.activate s (->certificate-activate-params id))] (mapv certificate-activate->map (impl/all-pages p)))))
(defn certificate-deactivate [^OpenAIClient client ^String id] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateDeactivatePage p (.deactivate s (->certificate-deactivate-params id))] (mapv certificate-deactivate->map (impl/all-pages p)))))

(defn- data-retention->map [^OrganizationDataRetention d] {:type (impl/->keyword (.type d))})
(defn- ->data-retention-update-params ^DataRetentionUpdateParams [{:keys [retention-type]}]
  (when-not retention-type (impl/missing-key! :retention-type))
  (-> (DataRetentionUpdateParams/builder) (.retentionType (DataRetentionUpdateParams$RetentionType/of (impl/enum-name retention-type))) (.build)))
(defn data-retention-retrieve [^OpenAIClient client] (impl/with-api-errors (let [^DataRetentionService s (.dataRetention (organization client))] (data-retention->map (.retrieve s)))))
(defn data-retention-update [^OpenAIClient client req] (impl/with-api-errors (let [^DataRetentionService s (.dataRetention (organization client))] (data-retention->map (.update s (->data-retention-update-params req))))))
(defn- ->group-create-params ^GroupCreateParams [{:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (GroupCreateParams/builder) (.name ^String name) (.build)))
(defn- ->group-update-params ^GroupUpdateParams [^String id {:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (GroupUpdateParams/builder) (.groupId id) (.name ^String name) (.build)))
(defn- ->group-list-params ^GroupListParams [{:keys [after limit order]}]
  (let [b (GroupListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (GroupListParams$Order/of (impl/enum-name order))))
    (.build b)))
(defn- group->map [^Group g]
  {:id (.id g) :created-at (.createdAt g) :group-type (impl/->keyword (.groupType g))
   :is-scim-managed (.isScimManaged g) :name (.name g)})
(defn- group-update->map [^GroupUpdateResponse g]
  {:id (.id g) :created-at (.createdAt g) :is-scim-managed (.isScimManaged g) :name (.name g)})
(defn group-create [^OpenAIClient client req]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client))] (group->map (.create s (->group-create-params req))))))
(defn group-retrieve [^OpenAIClient client ^String id]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client))] (group->map (.retrieve s id)))))
(defn group-update [^OpenAIClient client ^String id req]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client))] (group-update->map (.update s (->group-update-params id req))))))
(defn group-list
  ([^OpenAIClient client] (group-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^GroupService s (.groups (organization client)) ^GroupListPage p (.list s (->group-list-params opts))] (mapv group->map (impl/all-pages p))))))
(defn group-delete [^OpenAIClient client ^String id]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client)) ^GroupDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))
(defn- ->invite-project ^InviteCreateParams$Project [{:keys [id role]}]
  (when-not id (impl/missing-key! :id)) (when-not role (impl/missing-key! :role))
  (-> (InviteCreateParams$Project/builder) (.id ^String id) (.role (InviteCreateParams$Project$Role/of (impl/enum-name role))) (.build)))
(defn- ->invite-create-params ^InviteCreateParams [{:keys [email role projects]}]
  (when-not email (impl/missing-key! :email)) (when-not role (impl/missing-key! :role))
  (let [b (InviteCreateParams/builder)] (.email b ^String email) (.role b (InviteCreateParams$Role/of (impl/enum-name role)))
    (when projects (.projects b ^java.util.List (mapv ->invite-project projects))) (.build b)))
(defn- ->invite-list-params ^InviteListParams [{:keys [after limit]}]
  (let [b (InviteListParams/builder)] (when after (.after b ^String after)) (when limit (.limit b (long limit))) (.build b)))
(defn- invite-project->map [^Invite$Project p] {:id (.id p) :role (impl/->keyword (.role p))})
(defn- invite->map [^Invite i]
  (cond-> {:id (.id i) :created-at (.createdAt i) :email (.email i) :projects (mapv invite-project->map (.projects i)) :role (impl/->keyword (.role i)) :status (impl/->keyword (.status i))}
    (.isPresent (.acceptedAt i)) (assoc :accepted-at (impl/opt-get (.acceptedAt i)))
    (.isPresent (.expiresAt i)) (assoc :expires-at (impl/opt-get (.expiresAt i)))))
(defn invite-create [^OpenAIClient client req] (impl/with-api-errors (let [^InviteService s (.invites (organization client))] (invite->map (.create s (->invite-create-params req))))))
(defn invite-retrieve [^OpenAIClient client ^String id] (impl/with-api-errors (let [^InviteService s (.invites (organization client))] (invite->map (.retrieve s id)))))
(defn invite-list
  ([^OpenAIClient client] (invite-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^InviteService s (.invites (organization client)) ^InviteListPage p (.list s (->invite-list-params opts))] (mapv invite->map (impl/all-pages p))))))
(defn invite-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^InviteService s (.invites (organization client)) ^InviteDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))
(defn- ->project-update-params ^ProjectUpdateParams [^String project-id {:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (ProjectUpdateParams/builder) (.projectId project-id) (.name ^String name) (.build)))
(defn- ->project-list-params ^ProjectListParams [{:keys [after include-archived limit]}]
  (let [b (ProjectListParams/builder)]
    (when after (.after b ^String after))
    (when (some? include-archived) (.includeArchived b (boolean include-archived)))
    (when limit (.limit b (long limit)))
    (.build b)))
(defn- project->map [^Project p]
  (cond-> {:id (.id p) :created-at (.createdAt p)}
    (.isPresent (.archivedAt p)) (assoc :archived-at (impl/opt-get (.archivedAt p)))
    (.isPresent (.name p)) (assoc :name (impl/opt-get (.name p)))
    (.isPresent (.status p)) (assoc :status (impl/opt-get (.status p)))))
(defn project-create [^OpenAIClient client req]
  (impl/with-api-errors (let [^ProjectService s (.projects (organization client))] (project->map (.create s (->project-create-params req))))))
(defn project-retrieve [^OpenAIClient client ^String project-id]
  (impl/with-api-errors (let [^ProjectService s (.projects (organization client))] (project->map (.retrieve s project-id)))))
(defn project-update [^OpenAIClient client ^String project-id opts]
  (impl/with-api-errors (let [^ProjectService s (.projects (organization client))] (project->map (.update s (->project-update-params project-id opts))))))
(defn project-list
  ([^OpenAIClient client] (project-list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors (let [^ProjectService s (.projects (organization client)) ^ProjectListPage p (.list s (->project-list-params opts))] (mapv project->map (impl/all-pages p))))))
(defn project-archive [^OpenAIClient client ^String project-id]
  (impl/with-api-errors (let [^ProjectService s (.projects (organization client))] (project->map (.archive s project-id)))))
(defn- ->role-create-params ^RoleCreateParams [{:keys [role-name permissions description]}]
  (when-not role-name (impl/missing-key! :role-name))
  (when-not permissions (impl/missing-key! :permissions))
  (let [b (RoleCreateParams/builder)] (.roleName b ^String role-name) (.permissions b ^java.util.List permissions)
    (when description (.description b ^String description)) (.build b)))
(defn- ->role-update-params ^RoleUpdateParams [^String id {:keys [description permissions]}]
  (let [b (RoleUpdateParams/builder)] (.roleId b id)
    (when description (.description b ^String description))
    (when permissions (.permissions b ^java.util.List permissions)) (.build b)))
(defn- ->role-list-params ^RoleListParams [{:keys [after limit order]}]
  (let [b (RoleListParams/builder)] (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (RoleListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- role->map [^Role r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))))
(defn role-create [^OpenAIClient client req] (impl/with-api-errors (let [^RoleService s (.roles (organization client))] (role->map (.create s (->role-create-params req))))))
(defn role-retrieve [^OpenAIClient client ^String id] (impl/with-api-errors (let [^RoleService s (.roles (organization client))] (role->map (.retrieve s id)))))
(defn role-update [^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^RoleService s (.roles (organization client))] (role->map (.update s (->role-update-params id opts))))))
(defn role-list
  ([^OpenAIClient client] (role-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^RoleService s (.roles (organization client)) ^RoleListPage p (.list s (->role-list-params opts))] (mapv role->map (impl/all-pages p))))))
(defn role-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^RoleService s (.roles (organization client)) ^RoleDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))
(defn- ->spend-alert-create-channel ^SpendAlertCreateParams$NotificationChannel [{:keys [recipients subject-prefix type]}]
  (when-not recipients (impl/missing-key! :recipients)) (when-not type (impl/missing-key! :type))
  (let [b (SpendAlertCreateParams$NotificationChannel/builder)]
    (.recipients b ^java.util.List recipients) (.type b (JsonValue/from (impl/enum-name type)))
    (when subject-prefix (.subjectPrefix b ^String subject-prefix)) (.build b)))
(defn- ->spend-alert-create-params ^SpendAlertCreateParams [{:keys [currency interval notification-channel threshold-amount]}]
  (when-not currency (impl/missing-key! :currency)) (when-not interval (impl/missing-key! :interval))
  (when-not notification-channel (impl/missing-key! :notification-channel))
  (when-not threshold-amount (impl/missing-key! :threshold-amount))
  (-> (SpendAlertCreateParams/builder)
      (.currency (SpendAlertCreateParams$Currency/of (impl/enum-name currency)))
      (.interval (SpendAlertCreateParams$Interval/of (impl/enum-name interval)))
      (.notificationChannel (->spend-alert-create-channel notification-channel))
      (.thresholdAmount (long threshold-amount)) (.build)))
(defn- ->spend-alert-update-channel ^SpendAlertUpdateParams$NotificationChannel [{:keys [recipients subject-prefix type]}]
  (when-not recipients (impl/missing-key! :recipients)) (when-not type (impl/missing-key! :type))
  (let [b (SpendAlertUpdateParams$NotificationChannel/builder)]
    (.recipients b ^java.util.List recipients) (.type b (JsonValue/from (impl/enum-name type)))
    (when subject-prefix (.subjectPrefix b ^String subject-prefix)) (.build b)))
(defn- ->spend-alert-update-params ^SpendAlertUpdateParams [^String alert-id {:keys [currency interval notification-channel threshold-amount]}]
  (let [b (SpendAlertUpdateParams/builder)] (.alertId b alert-id)
    (when currency (.currency b (SpendAlertUpdateParams$Currency/of (impl/enum-name currency))))
    (when interval (.interval b (SpendAlertUpdateParams$Interval/of (impl/enum-name interval))))
    (when notification-channel (.notificationChannel b (->spend-alert-update-channel notification-channel)))
    (when threshold-amount (.thresholdAmount b (long threshold-amount))) (.build b)))
(defn- ->spend-alert-list-params ^SpendAlertListParams [{:keys [after before limit order]}]
  (let [b (SpendAlertListParams/builder)]
    (when after (.after b ^String after)) (when before (.before b ^String before))
    (when limit (.limit b (long limit)))
    (when order (.order b (SpendAlertListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- spend-alert-channel->map [^OrganizationSpendAlert$NotificationChannel c]
  (cond-> {:recipients (vec (.recipients c)) :type (impl/json-value->clj (._type c))}
    (.isPresent (.subjectPrefix c)) (assoc :subject-prefix (impl/opt-get (.subjectPrefix c)))))
(defn- spend-alert->map [^OrganizationSpendAlert a]
  {:id (.id a) :currency (impl/->keyword (.currency a)) :interval (impl/->keyword (.interval a))
   :notification-channel (spend-alert-channel->map (.notificationChannel a)) :threshold-amount (.thresholdAmount a)})
(defn spend-alert-create [^OpenAIClient client req] (impl/with-api-errors (let [^SpendAlertService s (.spendAlerts (organization client))] (spend-alert->map (.create s (->spend-alert-create-params req))))))
(defn spend-alert-retrieve [^OpenAIClient client ^String alert-id] (impl/with-api-errors (let [^SpendAlertService s (.spendAlerts (organization client))] (spend-alert->map (.retrieve s alert-id)))))
(defn spend-alert-update [^OpenAIClient client ^String alert-id opts] (impl/with-api-errors (let [^SpendAlertService s (.spendAlerts (organization client))] (spend-alert->map (.update s (->spend-alert-update-params alert-id opts))))))
(defn spend-alert-list
  ([^OpenAIClient client] (spend-alert-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^SpendAlertService s (.spendAlerts (organization client)) ^SpendAlertListPage p (.list s (->spend-alert-list-params opts))] (mapv spend-alert->map (impl/all-pages p))))))
(defn spend-alert-delete [^OpenAIClient client ^String alert-id]
  (impl/with-api-errors (let [^SpendAlertService s (.spendAlerts (organization client)) ^OrganizationSpendAlertDeleted r (.delete s alert-id)] {:id (.id r) :deleted (.deleted r)})))

;; Spend limits

(defn- ->spend-limit-update-params ^SpendLimitUpdateParams [{:keys [currency interval threshold-amount]}]
  (when-not currency (impl/missing-key! :currency)) (when-not interval (impl/missing-key! :interval))
  (when-not (some? threshold-amount) (impl/missing-key! :threshold-amount))
  (-> (SpendLimitUpdateParams/builder)
      (.currency (SpendLimitUpdateParams$Currency/of (impl/enum-name currency)))
      (.interval (SpendLimitUpdateParams$Interval/of (impl/enum-name interval)))
      (.thresholdAmount (long threshold-amount)) (.build)))
(defn- spend-limit->map [^OrganizationSpendLimit a]
  {:currency (impl/->keyword (.currency a)) :enforcement (impl/->keyword (.status (.enforcement a)))
   :interval (impl/->keyword (.interval a)) :threshold-amount (.thresholdAmount a)})
(defn- spend-limit-deleted->map [^OrganizationSpendLimitDeleted a] {:deleted (.deleted a)})
(defn spend-limit-retrieve [^OpenAIClient client]
  (impl/with-api-errors (let [^SpendLimitService s (.spendLimit (organization client))] (spend-limit->map (.retrieve s)))))
(defn spend-limit-update [^OpenAIClient client opts]
  (impl/with-api-errors (let [^SpendLimitService s (.spendLimit (organization client))] (spend-limit->map (.update s (->spend-limit-update-params opts))))))
(defn spend-limit-delete [^OpenAIClient client]
  (impl/with-api-errors (let [^SpendLimitService s (.spendLimit (organization client))] (spend-limit-deleted->map (.delete s)))))
(defn- ->user-update-params ^UserUpdateParams [^String id {:keys [developer-persona role role-id technical-level]}]
  (let [b (UserUpdateParams/builder)] (.userId b id)
    (when developer-persona (.developerPersona b ^String developer-persona))
    (when role (.role b ^String role)) (when role-id (.roleId b ^String role-id))
    (when technical-level (.technicalLevel b ^String technical-level)) (.build b)))
(defn- ->user-list-params ^UserListParams [{:keys [after limit]}]
  (let [b (UserListParams/builder)] (when after (.after b ^String after)) (when limit (.limit b (long limit))) (.build b)))
(defn- organization-user->map [^OrganizationUser u]
  (cond-> {:id (.id u) :added-at (.addedAt u)}
    (.isPresent (.apiKeyLastUsedAt u)) (assoc :api-key-last-used-at (impl/opt-get (.apiKeyLastUsedAt u)))
    (.isPresent (.created u)) (assoc :created (impl/opt-get (.created u)))
    (.isPresent (.developerPersona u)) (assoc :developer-persona (impl/opt-get (.developerPersona u)))
    (.isPresent (.email u)) (assoc :email (impl/opt-get (.email u)))
    (.isPresent (.isDefault u)) (assoc :is-default (impl/opt-get (.isDefault u)))
    (.isPresent (.isScaleTierAuthorizedPurchaser u)) (assoc :is-scale-tier-authorized-purchaser (impl/opt-get (.isScaleTierAuthorizedPurchaser u)))
    (.isPresent (.isScimManaged u)) (assoc :is-scim-managed (impl/opt-get (.isScimManaged u)))
    (.isPresent (.isServiceAccount u)) (assoc :is-service-account (impl/opt-get (.isServiceAccount u)))
    (.isPresent (.name u)) (assoc :name (impl/opt-get (.name u)))
    (.isPresent (.role u)) (assoc :role (impl/opt-get (.role u)))
    (.isPresent (.technicalLevel u)) (assoc :technical-level (impl/opt-get (.technicalLevel u)))))
(defn user-retrieve [^OpenAIClient client ^String id] (impl/with-api-errors (let [^UserService s (.users (organization client))] (organization-user->map (.retrieve s id)))))
(defn user-update [^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^UserService s (.users (organization client))] (organization-user->map (.update s (->user-update-params id opts))))))
(defn user-list
  ([^OpenAIClient client] (user-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^UserService s (.users (organization client)) ^UserListPage p (.list s (->user-list-params opts))] (mapv organization-user->map (impl/all-pages p))))))
(defn user-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^UserService s (.users (organization client)) ^UserDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))

(defn- ->group-role-create-params ^com.openai.models.admin.organization.groups.roles.RoleCreateParams [^String group-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.groups.roles.RoleCreateParams/builder) (.groupId group-id) (.roleId ^String role-id) (.build)))
(defn- ->group-role-retrieve-params ^com.openai.models.admin.organization.groups.roles.RoleRetrieveParams [^String group-id ^String role-id]
  (-> (com.openai.models.admin.organization.groups.roles.RoleRetrieveParams/builder) (.groupId group-id) (.roleId role-id) (.build)))
(defn- ->group-role-list-params ^com.openai.models.admin.organization.groups.roles.RoleListParams [^String group-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.groups.roles.RoleListParams/builder)] (.groupId b group-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.groups.roles.RoleListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- group-role-retrieve->map [^com.openai.models.admin.organization.groups.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn- group-role-list->map [^com.openai.models.admin.organization.groups.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn- group-role-create->map [^com.openai.models.admin.organization.groups.roles.RoleCreateResponse r]
  (let [^com.openai.models.admin.organization.groups.roles.RoleCreateResponse$Group g (.group r)]
    {:group {:id (.id g) :created-at (.createdAt g) :name (.name g) :scim-managed (.scimManaged g)} :role (role->map (.role r))}))
(defn group-role-create [^OpenAIClient client ^String group-id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client)))] (group-role-create->map (.create s (->group-role-create-params group-id req))))))
(defn group-role-retrieve [^OpenAIClient client ^String group-id ^String role-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client)))] (group-role-retrieve->map (.retrieve s (->group-role-retrieve-params group-id role-id))))))
(defn group-role-list
  ([^OpenAIClient client ^String group-id] (group-role-list client group-id {}))
  ([^OpenAIClient client ^String group-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client))) ^com.openai.models.admin.organization.groups.roles.RoleListPage p (.list s (->group-role-list-params group-id opts))] (mapv group-role-list->map (impl/all-pages p))))))
(defn group-role-delete [^OpenAIClient client ^String group-id ^String role-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client))) p (-> (com.openai.models.admin.organization.groups.roles.RoleDeleteParams/builder) (.groupId group-id) (.roleId role-id) (.build)) ^com.openai.models.admin.organization.groups.roles.RoleDeleteResponse r (.delete s p)] {:deleted (.deleted r)})))

(defn- ->group-user-create-params ^com.openai.models.admin.organization.groups.users.UserCreateParams [^String group-id {:keys [user-id]}]
  (when-not user-id (impl/missing-key! :user-id))
  (-> (com.openai.models.admin.organization.groups.users.UserCreateParams/builder) (.groupId group-id) (.userId ^String user-id) (.build)))
(defn- ->group-user-retrieve-params ^com.openai.models.admin.organization.groups.users.UserRetrieveParams [^String group-id ^String user-id]
  (-> (com.openai.models.admin.organization.groups.users.UserRetrieveParams/builder) (.groupId group-id) (.userId user-id) (.build)))
(defn- ->group-user-list-params ^com.openai.models.admin.organization.groups.users.UserListParams [^String group-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.groups.users.UserListParams/builder)] (.groupId b group-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.groups.users.UserListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- group-user-retrieve->map [^com.openai.models.admin.organization.groups.users.UserRetrieveResponse u]
  (cond-> {:id (.id u) :name (.name u) :user-type (impl/->keyword (.userType u))}
    (.isPresent (.email u)) (assoc :email (impl/opt-get (.email u)))
    (.isPresent (.isServiceAccount u)) (assoc :is-service-account (impl/opt-get (.isServiceAccount u)))
    (.isPresent (.picture u)) (assoc :picture (impl/opt-get (.picture u)))))
(defn- group-user-list->map [^com.openai.models.admin.organization.groups.users.OrganizationGroupUser u]
  (cond-> {:id (.id u) :name (.name u)} (.isPresent (.email u)) (assoc :email (impl/opt-get (.email u)))))
(defn group-user-create [^OpenAIClient client ^String group-id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client))) ^com.openai.models.admin.organization.groups.users.UserCreateResponse r (.create s (->group-user-create-params group-id req))] {:group-id (.groupId r) :user-id (.userId r)})))
(defn group-user-retrieve [^OpenAIClient client ^String group-id ^String user-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client)))] (group-user-retrieve->map (.retrieve s (->group-user-retrieve-params group-id user-id))))))
(defn group-user-list
  ([^OpenAIClient client ^String group-id] (group-user-list client group-id {}))
  ([^OpenAIClient client ^String group-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client))) ^com.openai.models.admin.organization.groups.users.UserListPage p (.list s (->group-user-list-params group-id opts))] (mapv group-user-list->map (impl/all-pages p))))))
(defn group-user-delete [^OpenAIClient client ^String group-id ^String user-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client))) p (-> (com.openai.models.admin.organization.groups.users.UserDeleteParams/builder) (.groupId group-id) (.userId user-id) (.build)) ^com.openai.models.admin.organization.groups.users.UserDeleteResponse r (.delete s p)] {:deleted (.deleted r)})))

(defn- ->user-role-create-params ^com.openai.models.admin.organization.users.roles.RoleCreateParams [^String user-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.users.roles.RoleCreateParams/builder) (.userId user-id) (.roleId ^String role-id) (.build)))
(defn- ->user-role-retrieve-params ^com.openai.models.admin.organization.users.roles.RoleRetrieveParams [^String user-id ^String role-id]
  (-> (com.openai.models.admin.organization.users.roles.RoleRetrieveParams/builder) (.userId user-id) (.roleId role-id) (.build)))
(defn- ->user-role-list-params ^com.openai.models.admin.organization.users.roles.RoleListParams [^String user-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.users.roles.RoleListParams/builder)] (.userId b user-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.users.roles.RoleListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- user-role-retrieve->map [^com.openai.models.admin.organization.users.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn- user-role-list->map [^com.openai.models.admin.organization.users.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn user-role-create [^OpenAIClient client ^String user-id req]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client))) ^com.openai.models.admin.organization.users.roles.RoleCreateResponse r (.create s (->user-role-create-params user-id req))] {:role (role->map (.role r)) :user (organization-user->map (.user r))})))
(defn user-role-retrieve [^OpenAIClient client ^String user-id ^String role-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client)))] (user-role-retrieve->map (.retrieve s (->user-role-retrieve-params user-id role-id))))))
(defn user-role-list
  ([^OpenAIClient client ^String user-id] (user-role-list client user-id {}))
  ([^OpenAIClient client ^String user-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client))) ^com.openai.models.admin.organization.users.roles.RoleListPage p (.list s (->user-role-list-params user-id opts))] (mapv user-role-list->map (impl/all-pages p))))))
(defn user-role-delete [^OpenAIClient client ^String user-id ^String role-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client))) p (-> (com.openai.models.admin.organization.users.roles.RoleDeleteParams/builder) (.userId user-id) (.roleId role-id) (.build)) ^com.openai.models.admin.organization.users.roles.RoleDeleteResponse r (.delete s p)] {:deleted (.deleted r)})))
(defn- usage-bucket-width [bucket-width]
  (case bucket-width :minute "1m" :hour "1h" :day "1d" (impl/enum-name bucket-width)))

(defn- ->usage-audio-speeches-params ^com.openai.models.admin.organization.usage.UsageAudioSpeechesParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids models project-ids user-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageAudioSpeechesParams$Builder b (com.openai.models.admin.organization.usage.UsageAudioSpeechesParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageAudioSpeechesParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageAudioSpeechesParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when models (.models b ^java.util.List models))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (.build b)))
(defn- usage-audio-speeches-result->map [^com.openai.models.admin.organization.usage.UsageAudioSpeechesResponse$Data$Result$OrganizationUsageAudioSpeechesResult r]
  (cond-> {:characters (.characters r) :num-model-requests (.numModelRequests r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.model r)) (assoc :model (impl/opt-get (.model r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))))
(defn- usage-audio-speeches-bucket->map [^com.openai.models.admin.organization.usage.UsageAudioSpeechesResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageAudioSpeechesResponse$Data$Result r] (usage-audio-speeches-result->map (.asOrganizationUsageAudioSpeeches r))) (.results bucket))})
(defn usage-audio-speeches [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageAudioSpeechesResponse response (.audioSpeeches s (->usage-audio-speeches-params request))
              result (into result (map usage-audio-speeches-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-audio-transcriptions-params ^com.openai.models.admin.organization.usage.UsageAudioTranscriptionsParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids models project-ids user-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageAudioTranscriptionsParams$Builder b (com.openai.models.admin.organization.usage.UsageAudioTranscriptionsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageAudioTranscriptionsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageAudioTranscriptionsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when models (.models b ^java.util.List models))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (.build b)))
(defn- usage-audio-transcriptions-result->map [^com.openai.models.admin.organization.usage.UsageAudioTranscriptionsResponse$Data$Result$OrganizationUsageAudioTranscriptionsResult r]
  (cond-> {:num-model-requests (.numModelRequests r) :seconds (.seconds r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.model r)) (assoc :model (impl/opt-get (.model r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))))
(defn- usage-audio-transcriptions-bucket->map [^com.openai.models.admin.organization.usage.UsageAudioTranscriptionsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageAudioTranscriptionsResponse$Data$Result r] (usage-audio-transcriptions-result->map (.asOrganizationUsageAudioTranscriptions r))) (.results bucket))})
(defn usage-audio-transcriptions [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageAudioTranscriptionsResponse response (.audioTranscriptions s (->usage-audio-transcriptions-params request))
              result (into result (map usage-audio-transcriptions-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-code-interpreter-sessions-params ^com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsParams [{:keys [start-time end-time bucket-width group-by limit page project-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsParams$Builder b (com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (.build b)))
(defn- usage-code-interpreter-sessions-result->map [^com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsResponse$Data$Result$OrganizationUsageCodeInterpreterSessionsResult r]
  (cond-> {:num-sessions (.numSessions r)}
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))))
(defn- usage-code-interpreter-sessions-bucket->map [^com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsResponse$Data$Result r] (usage-code-interpreter-sessions-result->map (.asOrganizationUsageCodeInterpreterSessions r))) (.results bucket))})
(defn usage-code-interpreter-sessions [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageCodeInterpreterSessionsResponse response (.codeInterpreterSessions s (->usage-code-interpreter-sessions-params request))
              result (into result (map usage-code-interpreter-sessions-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-completions-params ^com.openai.models.admin.organization.usage.UsageCompletionsParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids models project-ids user-ids batch]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageCompletionsParams$Builder b (com.openai.models.admin.organization.usage.UsageCompletionsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageCompletionsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageCompletionsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when models (.models b ^java.util.List models))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (when (some? batch) (.batch b (boolean batch)))
    (.build b)))
(defn- usage-completions-result->map [^com.openai.models.admin.organization.usage.UsageCompletionsResponse$Data$Result$OrganizationUsageCompletionsResult r]
  (cond-> {:input-tokens (.inputTokens r) :num-model-requests (.numModelRequests r) :output-tokens (.outputTokens r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.batch r)) (assoc :batch (impl/opt-get (.batch r)))
    (.isPresent (.inputAudioTokens r)) (assoc :input-audio-tokens (impl/opt-get (.inputAudioTokens r)))
    (.isPresent (.inputCachedTokens r)) (assoc :input-cached-tokens (impl/opt-get (.inputCachedTokens r)))
    (.isPresent (.model r)) (assoc :model (impl/opt-get (.model r)))
    (.isPresent (.outputAudioTokens r)) (assoc :output-audio-tokens (impl/opt-get (.outputAudioTokens r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.serviceTier r)) (assoc :service-tier (impl/opt-get (.serviceTier r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))))
(defn- usage-completions-bucket->map [^com.openai.models.admin.organization.usage.UsageCompletionsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageCompletionsResponse$Data$Result r] (usage-completions-result->map (.asOrganizationUsageCompletions r))) (.results bucket))})
(defn usage-completions [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageCompletionsResponse response (.completions s (->usage-completions-params request))
              result (into result (map usage-completions-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-costs-params ^com.openai.models.admin.organization.usage.UsageCostsParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids line-items project-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageCostsParams$Builder b (com.openai.models.admin.organization.usage.UsageCostsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageCostsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageCostsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when line-items (.lineItems b ^java.util.List line-items))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (.build b)))
(defn- usage-cost-amount->map [^com.openai.models.admin.organization.usage.UsageCostsResponse$Data$Result$OrganizationCostsResult$Amount a]
  (cond-> {} (.isPresent (.currency a)) (assoc :currency (impl/opt-get (.currency a)))
    (.isPresent (.value a)) (assoc :value (impl/opt-get (.value a)))))
(defn- usage-costs-result->map [^com.openai.models.admin.organization.usage.UsageCostsResponse$Data$Result$OrganizationCostsResult r]
  (cond-> {}
    (.isPresent (.amount r)) (assoc :amount (usage-cost-amount->map (impl/opt-get (.amount r))))
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.lineItem r)) (assoc :line-item (impl/opt-get (.lineItem r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.quantity r)) (assoc :quantity (impl/opt-get (.quantity r)))))
(defn- usage-costs-bucket->map [^com.openai.models.admin.organization.usage.UsageCostsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageCostsResponse$Data$Result r] (usage-costs-result->map (.asOrganizationCosts r))) (.results bucket))})
(defn usage-costs
  "List organization usage costs.

  Options include `:start-time` (required), `:end-time`, `:bucket-width`,
  `:group-by`, `:limit`, `:page`, `:api-key-ids`, `:line-items`, and
  `:project-ids`."
  [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageCostsResponse response (.costs s (->usage-costs-params request))
              result (into result (map usage-costs-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-embeddings-params ^com.openai.models.admin.organization.usage.UsageEmbeddingsParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids models project-ids user-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageEmbeddingsParams$Builder b (com.openai.models.admin.organization.usage.UsageEmbeddingsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageEmbeddingsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageEmbeddingsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when models (.models b ^java.util.List models))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (.build b)))
(defn- usage-embeddings-result->map [^com.openai.models.admin.organization.usage.UsageEmbeddingsResponse$Data$Result$OrganizationUsageEmbeddingsResult r]
  (cond-> {:input-tokens (.inputTokens r) :num-model-requests (.numModelRequests r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.model r)) (assoc :model (impl/opt-get (.model r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))))
(defn- usage-embeddings-bucket->map [^com.openai.models.admin.organization.usage.UsageEmbeddingsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageEmbeddingsResponse$Data$Result r] (usage-embeddings-result->map (.asOrganizationUsageEmbeddings r))) (.results bucket))})
(defn usage-embeddings [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageEmbeddingsResponse response (.embeddings s (->usage-embeddings-params request))
              result (into result (map usage-embeddings-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-file-search-calls-params ^com.openai.models.admin.organization.usage.UsageFileSearchCallsParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids project-ids user-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageFileSearchCallsParams$Builder b (com.openai.models.admin.organization.usage.UsageFileSearchCallsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageFileSearchCallsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageFileSearchCallsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (.build b)))
(defn- usage-file-search-calls-result->map [^com.openai.models.admin.organization.usage.UsageFileSearchCallsResponse$Data$Result$OrganizationUsageFileSearchesResult r]
  (cond-> {:num-requests (.numRequests r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))
    (.isPresent (.vectorStoreId r)) (assoc :vector-store-id (impl/opt-get (.vectorStoreId r)))))
(defn- usage-file-search-calls-bucket->map [^com.openai.models.admin.organization.usage.UsageFileSearchCallsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageFileSearchCallsResponse$Data$Result r] (usage-file-search-calls-result->map (.asOrganizationUsageFileSearches r))) (.results bucket))})
(defn usage-file-search-calls [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageFileSearchCallsResponse response (.fileSearchCalls s (->usage-file-search-calls-params request))
              result (into result (map usage-file-search-calls-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-images-params ^com.openai.models.admin.organization.usage.UsageImagesParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids models project-ids user-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageImagesParams$Builder b (com.openai.models.admin.organization.usage.UsageImagesParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageImagesParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageImagesParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when models (.models b ^java.util.List models))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (.build b)))
(defn- usage-images-result->map [^com.openai.models.admin.organization.usage.UsageImagesResponse$Data$Result$OrganizationUsageImagesResult r]
  (cond-> {:images (.images r) :num-model-requests (.numModelRequests r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.model r)) (assoc :model (impl/opt-get (.model r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.size r)) (assoc :size (impl/opt-get (.size r)))
    (.isPresent (.source r)) (assoc :source (impl/opt-get (.source r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))))
(defn- usage-images-bucket->map [^com.openai.models.admin.organization.usage.UsageImagesResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageImagesResponse$Data$Result r] (usage-images-result->map (.asOrganizationUsageImages r))) (.results bucket))})
(defn usage-images [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageImagesResponse response (.images s (->usage-images-params request))
              result (into result (map usage-images-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-moderations-params ^com.openai.models.admin.organization.usage.UsageModerationsParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids models project-ids user-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageModerationsParams$Builder b (com.openai.models.admin.organization.usage.UsageModerationsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageModerationsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageModerationsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when models (.models b ^java.util.List models))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (.build b)))
(defn- usage-moderations-result->map [^com.openai.models.admin.organization.usage.UsageModerationsResponse$Data$Result$OrganizationUsageModerationsResult r]
  (cond-> {:input-tokens (.inputTokens r) :num-model-requests (.numModelRequests r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.model r)) (assoc :model (impl/opt-get (.model r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))))
(defn- usage-moderations-bucket->map [^com.openai.models.admin.organization.usage.UsageModerationsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageModerationsResponse$Data$Result r] (usage-moderations-result->map (.asOrganizationUsageModerations r))) (.results bucket))})
(defn usage-moderations [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageModerationsResponse response (.moderations s (->usage-moderations-params request))
              result (into result (map usage-moderations-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-vector-stores-params ^com.openai.models.admin.organization.usage.UsageVectorStoresParams [{:keys [start-time end-time bucket-width group-by limit page project-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageVectorStoresParams$Builder b (com.openai.models.admin.organization.usage.UsageVectorStoresParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageVectorStoresParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageVectorStoresParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (.build b)))
(defn- usage-vector-stores-result->map [^com.openai.models.admin.organization.usage.UsageVectorStoresResponse$Data$Result$OrganizationUsageVectorStoresResult r]
  (cond-> {:usage-bytes (.usageBytes r)}
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))))
(defn- usage-vector-stores-bucket->map [^com.openai.models.admin.organization.usage.UsageVectorStoresResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageVectorStoresResponse$Data$Result r] (usage-vector-stores-result->map (.asOrganizationUsageVectorStores r))) (.results bucket))})
(defn usage-vector-stores [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageVectorStoresResponse response (.vectorStores s (->usage-vector-stores-params request))
              result (into result (map usage-vector-stores-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))

(defn- ->usage-web-search-calls-params ^com.openai.models.admin.organization.usage.UsageWebSearchCallsParams [{:keys [start-time end-time bucket-width group-by limit page api-key-ids models project-ids user-ids]}]
  (when-not (some? start-time) (impl/missing-key! :start-time))
  (let [^com.openai.models.admin.organization.usage.UsageWebSearchCallsParams$Builder b (com.openai.models.admin.organization.usage.UsageWebSearchCallsParams/builder)]
    (.startTime b (long start-time))
    (when end-time (.endTime b (long end-time)))
    (when bucket-width (.bucketWidth b (com.openai.models.admin.organization.usage.UsageWebSearchCallsParams$BucketWidth/of (usage-bucket-width bucket-width))))
    (when group-by (.groupBy b ^java.util.List (mapv #(com.openai.models.admin.organization.usage.UsageWebSearchCallsParams$GroupBy/of (impl/enum-name %)) group-by)))
    (when limit (.limit b (long limit))) (when page (.page b ^String page))
    (when api-key-ids (.apiKeyIds b ^java.util.List api-key-ids))
    (when models (.models b ^java.util.List models))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when user-ids (.userIds b ^java.util.List user-ids))
    (.build b)))
(defn- usage-web-search-calls-result->map [^com.openai.models.admin.organization.usage.UsageWebSearchCallsResponse$Data$Result$OrganizationUsageWebSearchesResult r]
  (cond-> {:num-model-requests (.numModelRequests r) :num-requests (.numRequests r)}
    (.isPresent (.apiKeyId r)) (assoc :api-key-id (impl/opt-get (.apiKeyId r)))
    (.isPresent (.contextLevel r)) (assoc :context-level (impl/opt-get (.contextLevel r)))
    (.isPresent (.model r)) (assoc :model (impl/opt-get (.model r)))
    (.isPresent (.projectId r)) (assoc :project-id (impl/opt-get (.projectId r)))
    (.isPresent (.userId r)) (assoc :user-id (impl/opt-get (.userId r)))))
(defn- usage-web-search-calls-bucket->map [^com.openai.models.admin.organization.usage.UsageWebSearchCallsResponse$Data bucket]
  {:start-time (.startTime bucket) :end-time (.endTime bucket)
   :results (mapv (fn [^com.openai.models.admin.organization.usage.UsageWebSearchCallsResponse$Data$Result r] (usage-web-search-calls-result->map (.asOrganizationUsageWebSearches r))) (.results bucket))})
(defn usage-web-search-calls [^OpenAIClient client opts]
  (impl/with-api-errors
    (let [^UsageService s (.usage (organization client))]
      (loop [request opts, result []]
        (let [^com.openai.models.admin.organization.usage.UsageWebSearchCallsResponse response (.webSearchCalls s (->usage-web-search-calls-params request))
              result (into result (map usage-web-search-calls-bucket->map (.data response)))
              next-page (impl/opt-get (.nextPage response))]
          (if next-page (recur (assoc opts :page next-page) result) result))))))
