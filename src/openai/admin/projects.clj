(ns openai.admin.projects
  "Project-scoped OpenAI Admin API wrappers."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonField)
           (com.openai.models.admin.organization.projects.apikeys ApiKeyDeleteParams ApiKeyDeleteResponse ApiKeyListPage ApiKeyListParams ApiKeyListParams$OwnerProjectAccess ApiKeyRetrieveParams ProjectApiKey ProjectApiKey$Owner ProjectApiKey$Owner$ServiceAccount ProjectApiKey$Owner$User)
           (com.openai.models.admin.organization.projects.certificates CertificateActivatePage CertificateActivateParams CertificateActivateResponse CertificateActivateResponse$CertificateDetails CertificateDeactivatePage CertificateDeactivateParams CertificateDeactivateResponse CertificateDeactivateResponse$CertificateDetails CertificateListPage CertificateListParams CertificateListParams$Order CertificateListResponse CertificateListResponse$CertificateDetails)
           (com.openai.models.admin.organization.projects.dataretention DataRetentionRetrieveParams DataRetentionUpdateParams DataRetentionUpdateParams$RetentionType ProjectDataRetention)
           (com.openai.models.admin.organization.projects.groups GroupCreateParams GroupDeleteParams GroupDeleteResponse GroupListPage GroupListParams GroupListParams$Order GroupRetrieveParams GroupRetrieveParams$GroupType ProjectGroup)
           (com.openai.models.admin.organization.projects.serviceaccounts.apikeys ApiKeyCreateParams ApiKeyCreateResponse)
           (com.openai.models.admin.organization.projects.spendlimit ProjectSpendLimit ProjectSpendLimitDeleted SpendLimitUpdateParams SpendLimitUpdateParams$Currency SpendLimitUpdateParams$Interval)
           (com.openai.models.admin.organization.roles Role)
           (com.openai.models.admin.organization.users OrganizationUser)
           (com.openai.services.blocking.admin OrganizationService)
           (com.openai.services.blocking.admin.organization ProjectService)
           (com.openai.services.blocking.admin.organization.projects ApiKeyService CertificateService DataRetentionService GroupService SpendLimitService)
           (com.openai.services.blocking.admin.organization.projects.groups RoleService)))

(set! *warn-on-reflection* true)

(defn- projects-service ^ProjectService [^OpenAIClient client]
  (let [^OrganizationService organization (.organization (.admin client))]
    (.projects organization)))

;; API keys

(defn- ->api-key-retrieve-params ^ApiKeyRetrieveParams [^String project-id ^String key-id]
  (-> (ApiKeyRetrieveParams/builder) (.projectId project-id) (.apiKeyId key-id) (.build)))

(defn- ->api-key-list-params ^ApiKeyListParams [^String project-id {:keys [after limit owner-project-access]}]
  (let [b (ApiKeyListParams/builder)]
    (.projectId b project-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when owner-project-access (.ownerProjectAccess b (ApiKeyListParams$OwnerProjectAccess/of (impl/enum-name owner-project-access))))
    (.build b)))

(defn- api-key-service-account->map [^ProjectApiKey$Owner$ServiceAccount a]
  {:id (.id a) :created-at (.createdAt a) :name (.name a) :role (.role a)})

(defn- api-key-user->map [^ProjectApiKey$Owner$User u]
  {:id (.id u) :created-at (.createdAt u) :email (.email u) :name (.name u) :role (.role u)})

(defn- api-key-owner->map [^ProjectApiKey$Owner o]
  (cond-> {}
    (.isPresent (.serviceAccount o)) (assoc :service-account (api-key-service-account->map (impl/opt-get (.serviceAccount o))))
    (.isPresent (.type o)) (assoc :type (impl/->keyword (impl/opt-get (.type o))))
    (.isPresent (.user o)) (assoc :user (api-key-user->map (impl/opt-get (.user o))))))

(defn- project-api-key->map [^ProjectApiKey k]
  (let [^JsonField owner-project-access (._ownerProjectAccess k)]
    (cond-> {:id (.id k) :created-at (.createdAt k) :name (.name k)
             :owner (api-key-owner->map (.owner k)) :redacted-value (.redactedValue k)}
      (.isPresent (.expiresAt k)) (assoc :expires-at (impl/opt-get (.expiresAt k)))
      (.isPresent (.lastUsedAt k)) (assoc :last-used-at (impl/opt-get (.lastUsedAt k)))
      (and (not (.isMissing owner-project-access)) (not (.isNull owner-project-access)))
      (assoc :owner-project-access (impl/->keyword (.asString (.ownerProjectAccess k)))))))

(defn api-key-retrieve [^OpenAIClient client ^String project-id ^String key-id]
  (impl/with-api-errors
    (let [^ApiKeyService svc (.apiKeys (projects-service client))]
      (project-api-key->map (.retrieve svc (->api-key-retrieve-params project-id key-id))))))

(defn api-key-list
  "List project API keys. Opts accepts :after, :limit, and :owner-project-access
  (:active, :inactive, or :any)."
  ([^OpenAIClient client ^String project-id] (api-key-list client project-id {}))
  ([^OpenAIClient client ^String project-id opts]
   (impl/with-api-errors
     (let [^ApiKeyService svc (.apiKeys (projects-service client))
           ^ApiKeyListPage page (.list svc (->api-key-list-params project-id opts))]
       (mapv project-api-key->map (impl/all-pages page))))))

(defn api-key-delete [^OpenAIClient client ^String project-id ^String key-id]
  (impl/with-api-errors
    (let [^ApiKeyService svc (.apiKeys (projects-service client))
          p (-> (ApiKeyDeleteParams/builder) (.projectId project-id) (.apiKeyId key-id) (.build))
          ^ApiKeyDeleteResponse r (.delete svc p)]
      {:id (.id r) :deleted (.deleted r)})))

;; Certificates

(defn- ->certificate-list-params ^CertificateListParams [^String project-id {:keys [after limit order]}]
  (let [b (CertificateListParams/builder)]
    (.projectId b project-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (CertificateListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- certificate-list-details->map [^CertificateListResponse$CertificateDetails d]
  (cond-> {}
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))

(defn- certificate-list->map [^CertificateListResponse c]
  (cond-> {:id (.id c) :active (.active c)
           :certificate-details (certificate-list-details->map (.certificateDetails c))
           :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))

(defn- ->certificate-activate-params ^CertificateActivateParams [^String project-id {:keys [certificate-ids]}]
  (when-not certificate-ids (impl/missing-key! :certificate-ids))
  (-> (CertificateActivateParams/builder) (.projectId project-id)
      (.certificateIds ^java.util.List certificate-ids) (.build)))

(defn- ->certificate-deactivate-params ^CertificateDeactivateParams [^String project-id {:keys [certificate-ids]}]
  (when-not certificate-ids (impl/missing-key! :certificate-ids))
  (-> (CertificateDeactivateParams/builder) (.projectId project-id)
      (.certificateIds ^java.util.List certificate-ids) (.build)))

(defn- certificate-activate-details->map [^CertificateActivateResponse$CertificateDetails d]
  (cond-> {}
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))

(defn- certificate-deactivate-details->map [^CertificateDeactivateResponse$CertificateDetails d]
  (cond-> {}
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))

(defn- certificate-activate->map [^CertificateActivateResponse c]
  (cond-> {:id (.id c) :active (.active c)
           :certificate-details (certificate-activate-details->map (.certificateDetails c))
           :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))

(defn- certificate-deactivate->map [^CertificateDeactivateResponse c]
  (cond-> {:id (.id c) :active (.active c)
           :certificate-details (certificate-deactivate-details->map (.certificateDetails c))
           :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))

(defn certificate-list
  ([^OpenAIClient client ^String project-id] (certificate-list client project-id {}))
  ([^OpenAIClient client ^String project-id opts]
   (impl/with-api-errors
     (let [^CertificateService svc (.certificates (projects-service client))
           ^CertificateListPage page (.list svc (->certificate-list-params project-id opts))]
       (mapv certificate-list->map (impl/all-pages page))))))

(defn certificate-activate [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^CertificateService svc (.certificates (projects-service client))
          ^CertificateActivatePage page (.activate svc (->certificate-activate-params project-id req))]
      (mapv certificate-activate->map (impl/all-pages page)))))

(defn certificate-deactivate [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^CertificateService svc (.certificates (projects-service client))
          ^CertificateDeactivatePage page (.deactivate svc (->certificate-deactivate-params project-id req))]
      (mapv certificate-deactivate->map (impl/all-pages page)))))

;; Data retention

(defn- ->data-retention-retrieve-params ^DataRetentionRetrieveParams [^String project-id]
  (-> (DataRetentionRetrieveParams/builder) (.projectId project-id) (.build)))

(defn- ->data-retention-update-params ^DataRetentionUpdateParams [^String project-id {:keys [retention-type]}]
  (when-not retention-type (impl/missing-key! :retention-type))
  (-> (DataRetentionUpdateParams/builder) (.projectId project-id)
      (.retentionType (DataRetentionUpdateParams$RetentionType/of (impl/enum-name retention-type)))
      (.build)))

(defn- data-retention->map [^ProjectDataRetention d]
  {:type (impl/->keyword (.type d))})

(defn data-retention-retrieve [^OpenAIClient client ^String project-id]
  (impl/with-api-errors
    (let [^DataRetentionService svc (.dataRetention (projects-service client))]
      (data-retention->map (.retrieve svc (->data-retention-retrieve-params project-id))))))

(defn data-retention-update [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^DataRetentionService svc (.dataRetention (projects-service client))]
      (data-retention->map (.update svc (->data-retention-update-params project-id req))))))

;; Groups

(defn- ->group-create-params ^GroupCreateParams [^String project-id {:keys [group-id role]}]
  (when-not group-id (impl/missing-key! :group-id))
  (when-not role (impl/missing-key! :role))
  (-> (GroupCreateParams/builder) (.projectId project-id) (.groupId ^String group-id)
      (.role ^String role) (.build)))

(defn- ->group-retrieve-params ^GroupRetrieveParams [^String project-id ^String group-id {:keys [group-type]}]
  (let [b (GroupRetrieveParams/builder)]
    (.projectId b project-id)
    (.groupId b group-id)
    (when group-type (.groupType b (GroupRetrieveParams$GroupType/of (impl/enum-name group-type))))
    (.build b)))

(defn- ->group-list-params ^GroupListParams [^String project-id {:keys [after limit order]}]
  (let [b (GroupListParams/builder)]
    (.projectId b project-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (GroupListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- project-group->map [^ProjectGroup g]
  {:created-at (.createdAt g) :group-id (.groupId g) :group-name (.groupName g)
   :group-type (impl/->keyword (.groupType g)) :project-id (.projectId g)})

(defn group-create [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^GroupService svc (.groups (projects-service client))]
      (project-group->map (.create svc (->group-create-params project-id req))))))

(defn group-retrieve
  ([^OpenAIClient client ^String project-id ^String group-id]
   (group-retrieve client project-id group-id {}))
  ([^OpenAIClient client ^String project-id ^String group-id opts]
   (impl/with-api-errors
     (let [^GroupService svc (.groups (projects-service client))]
       (project-group->map (.retrieve svc (->group-retrieve-params project-id group-id opts)))))))

(defn group-list
  ([^OpenAIClient client ^String project-id] (group-list client project-id {}))
  ([^OpenAIClient client ^String project-id opts]
   (impl/with-api-errors
     (let [^GroupService svc (.groups (projects-service client))
           ^GroupListPage page (.list svc (->group-list-params project-id opts))]
       (mapv project-group->map (impl/all-pages page))))))

(defn group-delete [^OpenAIClient client ^String project-id ^String group-id]
  (impl/with-api-errors
    (let [^GroupService svc (.groups (projects-service client))
          p (-> (GroupDeleteParams/builder) (.projectId project-id) (.groupId group-id) (.build))
          ^GroupDeleteResponse r (.delete svc p)]
      {:deleted (.deleted r)})))

;; Project group roles

(defn- ->group-role-create-params ^com.openai.models.admin.organization.projects.groups.roles.RoleCreateParams [^String project-id ^String group-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.projects.groups.roles.RoleCreateParams/builder)
      (.projectId project-id) (.groupId group-id) (.roleId ^String role-id) (.build)))

(defn- ->group-role-retrieve-params ^com.openai.models.admin.organization.projects.groups.roles.RoleRetrieveParams [^String project-id ^String group-id ^String role-id]
  (-> (com.openai.models.admin.organization.projects.groups.roles.RoleRetrieveParams/builder)
      (.projectId project-id) (.groupId group-id) (.roleId role-id) (.build)))

(defn- ->group-role-list-params ^com.openai.models.admin.organization.projects.groups.roles.RoleListParams [^String project-id ^String group-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.projects.groups.roles.RoleListParams/builder)]
    (.projectId b project-id) (.groupId b group-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.projects.groups.roles.RoleListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- role->map [^Role r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))))

(defn- group-role-retrieve->map [^com.openai.models.admin.organization.projects.groups.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn- group-role-list->map [^com.openai.models.admin.organization.projects.groups.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn- group-role-create->map [^com.openai.models.admin.organization.projects.groups.roles.RoleCreateResponse r]
  (let [^com.openai.models.admin.organization.projects.groups.roles.RoleCreateResponse$Group g (.group r)]
    {:group {:id (.id g) :created-at (.createdAt g) :name (.name g) :scim-managed (.scimManaged g)}
     :role (role->map (.role r))}))

(defn group-role-create [^OpenAIClient client ^String project-id ^String group-id req]
  (impl/with-api-errors
    (let [^RoleService svc (.roles (.groups (projects-service client)))]
      (group-role-create->map (.create svc (->group-role-create-params project-id group-id req))))))

(defn group-role-retrieve [^OpenAIClient client ^String project-id ^String group-id ^String role-id]
  (impl/with-api-errors
    (let [^RoleService svc (.roles (.groups (projects-service client)))]
      (group-role-retrieve->map (.retrieve svc (->group-role-retrieve-params project-id group-id role-id))))))

(defn group-role-list
  ([^OpenAIClient client ^String project-id ^String group-id]
   (group-role-list client project-id group-id {}))
  ([^OpenAIClient client ^String project-id ^String group-id opts]
   (impl/with-api-errors
     (let [^RoleService svc (.roles (.groups (projects-service client)))
           ^com.openai.models.admin.organization.projects.groups.roles.RoleListPage page
           (.list svc (->group-role-list-params project-id group-id opts))]
       (mapv group-role-list->map (impl/all-pages page))))))

(defn group-role-delete [^OpenAIClient client ^String project-id ^String group-id ^String role-id]
  (impl/with-api-errors
    (let [^RoleService svc (.roles (.groups (projects-service client)))
          p (-> (com.openai.models.admin.organization.projects.groups.roles.RoleDeleteParams/builder)
                (.projectId project-id) (.groupId group-id) (.roleId role-id) (.build))
          ^com.openai.models.admin.organization.projects.groups.roles.RoleDeleteResponse r (.delete svc p)]
      {:deleted (.deleted r)})))

;; Project user roles

(defn- ->user-role-create-params ^com.openai.models.admin.organization.projects.users.roles.RoleCreateParams [^String project-id ^String user-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.projects.users.roles.RoleCreateParams/builder)
      (.projectId project-id) (.userId user-id) (.roleId ^String role-id) (.build)))

(defn- ->user-role-retrieve-params ^com.openai.models.admin.organization.projects.users.roles.RoleRetrieveParams [^String project-id ^String user-id ^String role-id]
  (-> (com.openai.models.admin.organization.projects.users.roles.RoleRetrieveParams/builder)
      (.projectId project-id) (.userId user-id) (.roleId role-id) (.build)))

(defn- ->user-role-list-params ^com.openai.models.admin.organization.projects.users.roles.RoleListParams [^String project-id ^String user-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.projects.users.roles.RoleListParams/builder)]
    (.projectId b project-id) (.userId b user-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.projects.users.roles.RoleListParams$Order/of (impl/enum-name order))))
    (.build b)))

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

(defn- user-role-retrieve->map [^com.openai.models.admin.organization.projects.users.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn- user-role-list->map [^com.openai.models.admin.organization.projects.users.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn user-role-create [^OpenAIClient client ^String project-id ^String user-id req]
  (impl/with-api-errors
    (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
          (.roles (.users (projects-service client)))
          ^com.openai.models.admin.organization.projects.users.roles.RoleCreateResponse r
          (.create svc (->user-role-create-params project-id user-id req))]
      {:role (role->map (.role r)) :user (organization-user->map (.user r))})))

(defn user-role-retrieve [^OpenAIClient client ^String project-id ^String user-id ^String role-id]
  (impl/with-api-errors
    (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
          (.roles (.users (projects-service client)))]
      (user-role-retrieve->map (.retrieve svc (->user-role-retrieve-params project-id user-id role-id))))))

(defn user-role-list
  ([^OpenAIClient client ^String project-id ^String user-id]
   (user-role-list client project-id user-id {}))
  ([^OpenAIClient client ^String project-id ^String user-id opts]
   (impl/with-api-errors
     (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
           (.roles (.users (projects-service client)))
           ^com.openai.models.admin.organization.projects.users.roles.RoleListPage page
           (.list svc (->user-role-list-params project-id user-id opts))]
       (mapv user-role-list->map (impl/all-pages page))))))

(defn user-role-delete [^OpenAIClient client ^String project-id ^String user-id ^String role-id]
  (impl/with-api-errors
    (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
          (.roles (.users (projects-service client)))
          p (-> (com.openai.models.admin.organization.projects.users.roles.RoleDeleteParams/builder)
                (.projectId project-id) (.userId user-id) (.roleId role-id) (.build))
          ^com.openai.models.admin.organization.projects.users.roles.RoleDeleteResponse r (.delete svc p)]
      {:deleted (.deleted r)})))

;; Hosted tool permissions

(defn- ->hosted-tool-permission-retrieve-params ^com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionRetrieveParams [^String project-id]
  (-> (com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionRetrieveParams/builder)
      (.projectId project-id) (.build)))

(defn- ->hosted-tool-permission-update-params ^com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionUpdateParams [^String project-id opts]
  (let [b (com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionUpdateParams/builder)]
    (.projectId b project-id)
    (when-let [v (:code-interpreter opts)] (.codeInterpreter b (-> (com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionUpdateParams$CodeInterpreter/builder) (.enabled (boolean (:enabled v))) (.build))))
    (when-let [v (:file-search opts)] (.fileSearch b (-> (com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionUpdateParams$FileSearch/builder) (.enabled (boolean (:enabled v))) (.build))))
    (when-let [v (:image-generation opts)] (.imageGeneration b (-> (com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionUpdateParams$ImageGeneration/builder) (.enabled (boolean (:enabled v))) (.build))))
    (when-let [v (:mcp opts)] (.mcp b (-> (com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionUpdateParams$Mcp/builder) (.enabled (boolean (:enabled v))) (.build))))
    (when-let [v (:web-search opts)] (.webSearch b (-> (com.openai.models.admin.organization.projects.hostedtoolpermissions.HostedToolPermissionUpdateParams$WebSearch/builder) (.enabled (boolean (:enabled v))) (.build))))
    (.build b)))

(defn- hosted-tool-permission->map [^com.openai.models.admin.organization.projects.hostedtoolpermissions.ProjectHostedToolPermissions p]
  {:code-interpreter {:enabled (.enabled (.codeInterpreter p))}
   :file-search {:enabled (.enabled (.fileSearch p))}
   :image-generation {:enabled (.enabled (.imageGeneration p))}
   :mcp {:enabled (.enabled (.mcp p))}
   :web-search {:enabled (.enabled (.webSearch p))}})

(defn hosted-tool-permission-retrieve [^OpenAIClient client ^String project-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.HostedToolPermissionService s (.hostedToolPermissions (projects-service client))]
                          (hosted-tool-permission->map (.retrieve s (->hosted-tool-permission-retrieve-params project-id))))))
(defn hosted-tool-permission-update [^OpenAIClient client ^String project-id opts]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.HostedToolPermissionService s (.hostedToolPermissions (projects-service client))]
                          (hosted-tool-permission->map (.update s (->hosted-tool-permission-update-params project-id opts))))))

;; Model permissions

(defn- model-permission->map [^com.openai.models.admin.organization.projects.modelpermissions.ProjectModelPermissions p]
  {:mode (impl/->keyword (.mode p)) :model-ids (vec (.modelIds p))})
(defn- ->model-permission-update-params [^String project-id {:keys [mode model-ids]}]
  (when-not mode (impl/missing-key! :mode)) (when-not model-ids (impl/missing-key! :model-ids))
  (-> (com.openai.models.admin.organization.projects.modelpermissions.ModelPermissionUpdateParams/builder)
      (.projectId project-id) (.mode (com.openai.models.admin.organization.projects.modelpermissions.ModelPermissionUpdateParams$Mode/of (impl/enum-name mode)))
      (.modelIds ^java.util.List model-ids) (.build)))
(defn model-permission-retrieve [^OpenAIClient client ^String project-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.ModelPermissionService s (.modelPermissions (projects-service client))]
                          (model-permission->map (.retrieve s (-> (com.openai.models.admin.organization.projects.modelpermissions.ModelPermissionRetrieveParams/builder) (.projectId project-id) (.build)))))))
(defn model-permission-update [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.ModelPermissionService s (.modelPermissions (projects-service client))]
                          (model-permission->map (.update s (->model-permission-update-params project-id req))))))
(defn model-permission-delete [^OpenAIClient client ^String project-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.ModelPermissionService s (.modelPermissions (projects-service client))
                              ^com.openai.models.admin.organization.projects.modelpermissions.ProjectModelPermissionsDeleted r
                              (.delete s (-> (com.openai.models.admin.organization.projects.modelpermissions.ModelPermissionDeleteParams/builder) (.projectId project-id) (.build)))]
                          {:deleted (.deleted r)})))

;; Rate limits

(defn- ->rate-limit-list-params ^com.openai.models.admin.organization.projects.ratelimits.RateLimitListRateLimitsParams [^String project-id {:keys [after before limit]}]
  (let [b (com.openai.models.admin.organization.projects.ratelimits.RateLimitListRateLimitsParams/builder)]
    (.projectId b project-id) (when after (.after b ^String after)) (when before (.before b ^String before))
    (when limit (.limit b (long limit))) (.build b)))
(defn- ->rate-limit-update-params [^String project-id ^String rate-limit-id opts]
  (let [b (com.openai.models.admin.organization.projects.ratelimits.RateLimitUpdateRateLimitParams/builder)]
    (.projectId b project-id) (.rateLimitId b rate-limit-id)
    (doseq [[k setter] [[:batch-1-day-max-input-tokens #(.batch1DayMaxInputTokens b (long %))]
                        [:max-audio-megabytes-per-1-minute #(.maxAudioMegabytesPer1Minute b (long %))]
                        [:max-images-per-1-minute #(.maxImagesPer1Minute b (long %))]
                        [:max-requests-per-1-day #(.maxRequestsPer1Day b (long %))]
                        [:max-requests-per-1-minute #(.maxRequestsPer1Minute b (long %))]
                        [:max-tokens-per-1-minute #(.maxTokensPer1Minute b (long %))]]]
      (when-some [v (get opts k)] (setter v)))
    (.build b)))
(defn- rate-limit->map [^com.openai.models.admin.organization.projects.ratelimits.ProjectRateLimit r]
  (cond-> {:id (.id r) :max-requests-per-1-minute (.maxRequestsPer1Minute r)
           :max-tokens-per-1-minute (.maxTokensPer1Minute r) :model (.model r)}
    (.isPresent (.batch1DayMaxInputTokens r)) (assoc :batch-1-day-max-input-tokens (impl/opt-get (.batch1DayMaxInputTokens r)))
    (.isPresent (.maxAudioMegabytesPer1Minute r)) (assoc :max-audio-megabytes-per-1-minute (impl/opt-get (.maxAudioMegabytesPer1Minute r)))
    (.isPresent (.maxImagesPer1Minute r)) (assoc :max-images-per-1-minute (impl/opt-get (.maxImagesPer1Minute r)))
    (.isPresent (.maxRequestsPer1Day r)) (assoc :max-requests-per-1-day (impl/opt-get (.maxRequestsPer1Day r)))))
(defn rate-limit-list-rate-limits
  ([^OpenAIClient client ^String project-id] (rate-limit-list-rate-limits client project-id {}))
  ([^OpenAIClient client ^String project-id opts]
   (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.RateLimitService s (.rateLimits (projects-service client))
                               ^com.openai.models.admin.organization.projects.ratelimits.RateLimitListRateLimitsPage p (.listRateLimits s (->rate-limit-list-params project-id opts))]
                           (mapv rate-limit->map (impl/all-pages p))))))
(defn rate-limit-update-rate-limit [^OpenAIClient client ^String project-id ^String rate-limit-id opts]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.RateLimitService s (.rateLimits (projects-service client))]
                          (rate-limit->map (.updateRateLimit s (->rate-limit-update-params project-id rate-limit-id opts))))))

;; Project roles

(defn- ->role-create-params [^String project-id {:keys [permissions role-name description]}]
  (when-not permissions (impl/missing-key! :permissions)) (when-not role-name (impl/missing-key! :role-name))
  (let [b (com.openai.models.admin.organization.projects.roles.RoleCreateParams/builder)]
    (.projectId b project-id) (.permissions b ^java.util.List permissions) (.roleName b ^String role-name)
    (when description (.description b ^String description)) (.build b)))
(defn- ->role-update-params [^String project-id ^String role-id {:keys [description permissions role-name]}]
  (let [b (com.openai.models.admin.organization.projects.roles.RoleUpdateParams/builder)]
    (.projectId b project-id) (.roleId b role-id) (when description (.description b ^String description))
    (when permissions (.permissions b ^java.util.List permissions)) (when role-name (.roleName b ^String role-name)) (.build b)))
(defn- ->role-list-params ^com.openai.models.admin.organization.projects.roles.RoleListParams [^String project-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.projects.roles.RoleListParams/builder)]
    (.projectId b project-id) (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.projects.roles.RoleListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn role-create [^OpenAIClient client ^String project-id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.RoleService s (.roles (projects-service client))] (role->map (.create s (->role-create-params project-id req))))))
(defn role-retrieve [^OpenAIClient client ^String project-id ^String role-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.RoleService s (.roles (projects-service client))] (role->map (.retrieve s (-> (com.openai.models.admin.organization.projects.roles.RoleRetrieveParams/builder) (.projectId project-id) (.roleId role-id) (.build)))))))
(defn role-update [^OpenAIClient client ^String project-id ^String role-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.RoleService s (.roles (projects-service client))] (role->map (.update s (->role-update-params project-id role-id opts))))))
(defn role-list ([^OpenAIClient client ^String project-id] (role-list client project-id {})) ([^OpenAIClient client ^String project-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.RoleService s (.roles (projects-service client)) ^com.openai.models.admin.organization.projects.roles.RoleListPage p (.list s (->role-list-params project-id opts))] (mapv role->map (impl/all-pages p))))))
(defn role-delete [^OpenAIClient client ^String project-id ^String role-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.RoleService s (.roles (projects-service client)) ^com.openai.models.admin.organization.projects.roles.RoleDeleteResponse r (.delete s (-> (com.openai.models.admin.organization.projects.roles.RoleDeleteParams/builder) (.projectId project-id) (.roleId role-id) (.build)))] {:id (.id r) :deleted (.deleted r)})))

;; Service accounts

(defn- ->service-account-create-params [^String project-id {:keys [name expires-in-seconds]}]
  (when-not name (impl/missing-key! :name))
  (let [b (-> (com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountCreateParams/builder)
              (.projectId project-id)
              (.name ^String name))]
    (when expires-in-seconds (.expiresInSeconds b (long expires-in-seconds)))
    (.build b)))
(defn- service-account->map [^com.openai.models.admin.organization.projects.serviceaccounts.ProjectServiceAccount a] {:id (.id a) :created-at (.createdAt a) :name (.name a) :role (impl/->keyword (.role a))})
(defn- service-account-create->map [^com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountCreateResponse a]
  (cond-> {:id (.id a) :created-at (.createdAt a) :name (.name a)}
    (.isPresent (.apiKey a))
    (assoc :api-key
           (let [^com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountCreateResponse$ApiKey k
                 (impl/opt-get (.apiKey a))]
             (cond-> {:id (.id k) :created-at (.createdAt k)
                      :name (.name k) :value (.value k)}
               (.isPresent (.expiresAt k))
               (assoc :expires-at (impl/opt-get (.expiresAt k))))))))
(defn- ->service-account-list-params ^com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountListParams [^String project-id {:keys [after limit]}] (let [b (com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountListParams/builder)] (.projectId b project-id) (when after (.after b ^String after)) (when limit (.limit b (long limit))) (.build b)))
(defn service-account-create [^OpenAIClient client ^String project-id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.ServiceAccountService s (.serviceAccounts (projects-service client))] (service-account-create->map (.create s (->service-account-create-params project-id req))))))
(defn service-account-retrieve [^OpenAIClient client ^String project-id ^String id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.ServiceAccountService s (.serviceAccounts (projects-service client))] (service-account->map (.retrieve s (-> (com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountRetrieveParams/builder) (.projectId project-id) (.serviceAccountId id) (.build)))))))
(defn service-account-update [^OpenAIClient client ^String project-id ^String id {:keys [name role]}] (impl/with-api-errors (let [b (com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountUpdateParams/builder) ^com.openai.services.blocking.admin.organization.projects.ServiceAccountService s (.serviceAccounts (projects-service client))] (.projectId b project-id) (.serviceAccountId b id) (when name (.name b ^String name)) (when role (.role b (com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountUpdateParams$Role/of (impl/enum-name role)))) (service-account->map (.update s (.build b))))))
(defn service-account-list ([^OpenAIClient client ^String project-id] (service-account-list client project-id {})) ([^OpenAIClient client ^String project-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.ServiceAccountService s (.serviceAccounts (projects-service client)) ^com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountListPage p (.list s (->service-account-list-params project-id opts))] (mapv service-account->map (impl/all-pages p))))))
(defn service-account-delete [^OpenAIClient client ^String project-id ^String id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.ServiceAccountService s (.serviceAccounts (projects-service client)) ^com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountDeleteResponse r (.delete s (-> (com.openai.models.admin.organization.projects.serviceaccounts.ServiceAccountDeleteParams/builder) (.projectId project-id) (.serviceAccountId id) (.build)))] {:id (.id r) :deleted (.deleted r)})))

(defn- ->service-account-api-key-create-params
  ^ApiKeyCreateParams [^String project-id ^String service-account-id
                       {:keys [name scopes expires-in-seconds]}]
  (when-not project-id (impl/missing-key! :project-id)) (when-not service-account-id (impl/missing-key! :service-account-id))
  (let [b (ApiKeyCreateParams/builder)] (.projectId b project-id) (.serviceAccountId b service-account-id)
    (when name (.name b ^String name))
    (when scopes (.scopes b ^java.util.List scopes))
    (when expires-in-seconds (.expiresInSeconds b (long expires-in-seconds)))
    (.build b)))
(defn- service-account-api-key-create-response->map [^ApiKeyCreateResponse a]
  (cond-> {:id (.id a) :created-at (.createdAt a) :name (.name a) :value (.value a)}
    (.isPresent (.expiresAt a)) (assoc :expires-at (impl/opt-get (.expiresAt a)))))
(defn service-account-api-key-create [^OpenAIClient client ^String project-id ^String service-account-id opts]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.serviceaccounts.ApiKeyService s (.apiKeys (.serviceAccounts (projects-service client)))]
                          (service-account-api-key-create-response->map (.create s (->service-account-api-key-create-params project-id service-account-id opts))))))

;; Spend limits

(defn- ->spend-limit-update-params ^SpendLimitUpdateParams [^String project-id {:keys [currency interval threshold-amount]}]
  (when-not project-id (impl/missing-key! :project-id)) (when-not currency (impl/missing-key! :currency))
  (when-not interval (impl/missing-key! :interval)) (when-not (some? threshold-amount) (impl/missing-key! :threshold-amount))
  (-> (SpendLimitUpdateParams/builder) (.projectId project-id)
      (.currency (SpendLimitUpdateParams$Currency/of (impl/enum-name currency)))
      (.interval (SpendLimitUpdateParams$Interval/of (impl/enum-name interval)))
      (.thresholdAmount (long threshold-amount)) (.build)))
(defn- spend-limit->map [^ProjectSpendLimit a]
  {:currency (impl/->keyword (.currency a)) :enforcement (impl/->keyword (.status (.enforcement a)))
   :interval (impl/->keyword (.interval a)) :threshold-amount (.thresholdAmount a)})
(defn- spend-limit-deleted->map [^ProjectSpendLimitDeleted a] {:deleted (.deleted a)})
(defn spend-limit-retrieve [^OpenAIClient client ^String project-id]
  (impl/with-api-errors (let [^SpendLimitService s (.spendLimit (projects-service client))] (spend-limit->map (.retrieve s project-id)))))
(defn spend-limit-update [^OpenAIClient client ^String project-id opts]
  (impl/with-api-errors (let [^SpendLimitService s (.spendLimit (projects-service client))] (spend-limit->map (.update s project-id (->spend-limit-update-params project-id opts))))))
(defn spend-limit-delete [^OpenAIClient client ^String project-id]
  (impl/with-api-errors (let [^SpendLimitService s (.spendLimit (projects-service client))] (spend-limit-deleted->map (.delete s project-id)))))

;; Spend alerts

(defn- ->spend-alert-create-params ^com.openai.models.admin.organization.projects.spendalerts.SpendAlertCreateParams [^String project-id {:keys [currency interval notification-channel threshold-amount]}]
  (when-not currency (impl/missing-key! :currency)) (when-not interval (impl/missing-key! :interval)) (when-not notification-channel (impl/missing-key! :notification-channel)) (when-not (some? threshold-amount) (impl/missing-key! :threshold-amount))
  (-> (com.openai.models.admin.organization.projects.spendalerts.SpendAlertCreateParams/builder) (.projectId project-id)
      (.currency (com.openai.models.admin.organization.projects.spendalerts.SpendAlertCreateParams$Currency/of (impl/enum-name currency)))
      (.interval (com.openai.models.admin.organization.projects.spendalerts.SpendAlertCreateParams$Interval/of (impl/enum-name interval)))
      (.notificationChannel (-> (cond-> (com.openai.models.admin.organization.projects.spendalerts.SpendAlertCreateParams$NotificationChannel/builder)
                              true (.recipients ^java.util.List (:recipients notification-channel))
                              true (.type (com.openai.core.JsonValue/from (impl/enum-name (:type notification-channel))))
                              (:subject-prefix notification-channel) (.subjectPrefix ^String (:subject-prefix notification-channel))) (.build)))
      (.thresholdAmount (long threshold-amount)) (.build)))
(defn- ->spend-alert-update-params ^com.openai.models.admin.organization.projects.spendalerts.SpendAlertUpdateParams [^String project-id ^String alert-id {:keys [currency interval notification-channel threshold-amount]}]
  (when-not currency (impl/missing-key! :currency)) (when-not interval (impl/missing-key! :interval)) (when-not notification-channel (impl/missing-key! :notification-channel)) (when-not (some? threshold-amount) (impl/missing-key! :threshold-amount))
  (-> (com.openai.models.admin.organization.projects.spendalerts.SpendAlertUpdateParams/builder) (.projectId project-id) (.alertId alert-id)
      (.currency (com.openai.models.admin.organization.projects.spendalerts.SpendAlertUpdateParams$Currency/of (impl/enum-name currency)))
      (.interval (com.openai.models.admin.organization.projects.spendalerts.SpendAlertUpdateParams$Interval/of (impl/enum-name interval)))
      (.notificationChannel (-> (cond-> (com.openai.models.admin.organization.projects.spendalerts.SpendAlertUpdateParams$NotificationChannel/builder)
                              true (.recipients ^java.util.List (:recipients notification-channel))
                              true (.type (com.openai.core.JsonValue/from (impl/enum-name (:type notification-channel))))
                              (:subject-prefix notification-channel) (.subjectPrefix ^String (:subject-prefix notification-channel))) (.build)))
      (.thresholdAmount (long threshold-amount)) (.build)))
(defn- spend-alert->map [^com.openai.models.admin.organization.projects.spendalerts.ProjectSpendAlert a] {:id (.id a) :currency (impl/->keyword (.currency a)) :interval (impl/->keyword (.interval a)) :notification-channel {:recipients (vec (.recipients (.notificationChannel a))) :type (impl/json-value->clj (._type (.notificationChannel a)))} :threshold-amount (.thresholdAmount a)})
(defn- ->spend-alert-list-params ^com.openai.models.admin.organization.projects.spendalerts.SpendAlertListParams [^String project-id {:keys [after before limit order]}] (let [b (com.openai.models.admin.organization.projects.spendalerts.SpendAlertListParams/builder)] (.projectId b project-id) (when after (.after b ^String after)) (when before (.before b ^String before)) (when limit (.limit b (long limit))) (when order (.order b (com.openai.models.admin.organization.projects.spendalerts.SpendAlertListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn spend-alert-create [^OpenAIClient client ^String project-id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.SpendAlertService s (.spendAlerts (projects-service client))] (spend-alert->map (.create s (->spend-alert-create-params project-id req))))))
(defn spend-alert-retrieve [^OpenAIClient client ^String project-id ^String id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.SpendAlertService s (.spendAlerts (projects-service client))] (spend-alert->map (.retrieve s (-> (com.openai.models.admin.organization.projects.spendalerts.SpendAlertRetrieveParams/builder) (.projectId project-id) (.alertId id) (.build)))))))
(defn spend-alert-update [^OpenAIClient client ^String project-id ^String id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.SpendAlertService s (.spendAlerts (projects-service client))] (spend-alert->map (.update s (->spend-alert-update-params project-id id req))))))
(defn spend-alert-list ([^OpenAIClient client ^String project-id] (spend-alert-list client project-id {})) ([^OpenAIClient client ^String project-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.SpendAlertService s (.spendAlerts (projects-service client)) ^com.openai.models.admin.organization.projects.spendalerts.SpendAlertListPage p (.list s (->spend-alert-list-params project-id opts))] (mapv spend-alert->map (impl/all-pages p))))))
(defn spend-alert-delete [^OpenAIClient client ^String project-id ^String id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.SpendAlertService s (.spendAlerts (projects-service client)) ^com.openai.models.admin.organization.projects.spendalerts.ProjectSpendAlertDeleted r (.delete s (-> (com.openai.models.admin.organization.projects.spendalerts.SpendAlertDeleteParams/builder) (.projectId project-id) (.alertId id) (.build)))] {:id (.id r) :deleted (.deleted r)})))

;; Project users

(defn- user->map [^com.openai.models.admin.organization.projects.users.ProjectUser u] (cond-> {:id (.id u) :added-at (.addedAt u) :role (.role u)} (.isPresent (.email u)) (assoc :email (impl/opt-get (.email u))) (.isPresent (.name u)) (assoc :name (impl/opt-get (.name u)))))
(defn- ->user-list-params ^com.openai.models.admin.organization.projects.users.UserListParams [^String project-id {:keys [after limit]}] (let [b (com.openai.models.admin.organization.projects.users.UserListParams/builder)] (.projectId b project-id) (when after (.after b ^String after)) (when limit (.limit b (long limit))) (.build b)))
(defn user-create [^OpenAIClient client ^String project-id {:keys [role email user-id]}] (when-not role (impl/missing-key! :role)) (impl/with-api-errors (let [b (com.openai.models.admin.organization.projects.users.UserCreateParams/builder) ^com.openai.services.blocking.admin.organization.projects.UserService s (.users (projects-service client))] (.projectId b project-id) (.role b ^String role) (when email (.email b ^String email)) (when user-id (.userId b ^String user-id)) (user->map (.create s (.build b))))))
(defn user-retrieve [^OpenAIClient client ^String project-id ^String id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.UserService s (.users (projects-service client))] (user->map (.retrieve s (-> (com.openai.models.admin.organization.projects.users.UserRetrieveParams/builder) (.projectId project-id) (.userId id) (.build)))))))
(defn user-update [^OpenAIClient client ^String project-id ^String id {:keys [role]}] (impl/with-api-errors (let [b (com.openai.models.admin.organization.projects.users.UserUpdateParams/builder) ^com.openai.services.blocking.admin.organization.projects.UserService s (.users (projects-service client))] (.projectId b project-id) (.userId b id) (when role (.role b ^String role)) (user->map (.update s (.build b))))))
(defn user-list ([^OpenAIClient client ^String project-id] (user-list client project-id {})) ([^OpenAIClient client ^String project-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.UserService s (.users (projects-service client)) ^com.openai.models.admin.organization.projects.users.UserListPage p (.list s (->user-list-params project-id opts))] (mapv user->map (impl/all-pages p))))))
(defn user-delete [^OpenAIClient client ^String project-id ^String id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.projects.UserService s (.users (projects-service client)) ^com.openai.models.admin.organization.projects.users.UserDeleteResponse r (.delete s (-> (com.openai.models.admin.organization.projects.users.UserDeleteParams/builder) (.projectId project-id) (.userId id) (.build)))] {:id (.id r) :deleted (.deleted r)})))
