# Admin

`openai.admin` covers organization resources and usage. `openai.admin.projects`
covers project-scoped API keys, service accounts, limits, permissions, users,
groups, roles, retention, spend alerts, and certificates.

Admin wrappers take positional resource IDs such as project, group, user, and
role. They then take a kebab-case map for optional body or query values. This
matches the other library functions.

Organization spend limits use `spend-limit-retrieve`, `spend-limit-update`,
and `spend-limit-delete`. Project spend limits use the same functions in
`openai.admin.projects`, with a project ID. Use `service-account-api-key-create`
with project and service-account IDs to create a service-account API key.

External key management uses `external-key-create`, `external-key-retrieve`,
`external-key-list`, `external-key-delete`, and `external-key-validate`.
Create requests use a tagged provider map, such as `{:type :aws :name "..."
:external-id "..." :kms-arn "..." :role-arn "..."}`. Azure and GCP variants
use their SDK provider fields with kebab-case keys.

`usage-costs` requires `:start-time` and accepts `:end-time`, `:bucket-width`,
`:group-by`, `:limit`, `:page`, `:api-key-ids`, `:line-items`, and
`:project-ids`.

```clojure
(admin/project-create client {:name "research"})
(admin/group-role-create client "group_..." {:role-id "role_..."})
(admin/group-user-retrieve client "group_..." "user_...")
(admin-projects/user-list client "proj_..." {:limit 20})
```
