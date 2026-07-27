# Jenkins Shared Library (central)

Repo trung tâm cho pipeline CI của mọi project lab OCP.

**Hướng dẫn chi tiết (push, đăng ký Jenkins, chạy build, troubleshooting):**  
→ **[SETUP.md](./SETUP.md)**

## Tóm tắt

```text
@Library('platform@main') _
platformPipeline([ project: 'cinehome' | 'banking-demo' | 'aiops', … ])
```

| Field Jenkins Global Library | Value |
|------------------------------|--------|
| Name | `platform` |
| Repo | `https://github.com/kevinram164/jenkins-shared-library.git` |
| Default version | `main` |
| Library Path | *(trống)* |

Compat wrappers: `cinehomePipeline` / `bankingDemoPipeline` / `aiopsPipeline` (cùng repo).
