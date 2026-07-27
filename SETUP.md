# Hướng dẫn vận hành — Jenkins Shared Library trung tâm

Mục tiêu: **một** Global Library tên `platform` → mọi app (`movie-web`, `banking-demo`, `Open-Source-AIOps-Platform`) dùng chung, pipeline chạy ổn định.

---

## 0. Checklist trước khi build

| # | Việc | Pass khi |
|---|------|----------|
| 1 | Push code library lên GitHub `main` | Repo có `vars/` + `src/` |
| 2 | Jenkins đăng ký library `platform` | Build không còn `Could not find any definition of libraries [platform]` |
| 3 | SA `jenkins-kaniko` + SCC | Agent pod tạo được, Kaniko UID 0 |
| 4 | Vault role + secret Harbor/GitHub | Stage Build không fail login Vault |
| 5 | Harbor project tồn tại + robot push | Image lên Harbor |
| 6 | Jenkinsfile app đã `@Library('platform@main')` | Stage Checkout chạy |
| 7 | Lần đầu dùng `BUILD_TARGET=<service>` (không chỉ `auto`) | Tránh “success nhưng không build” vì không có diff |

---

## 1. Push repo library (bắt buộc trước)

Trên máy local, thư mục `jenkins-shared-library/` hiện có file **chưa commit**:

```powershell
cd D:\Tai-lieu\LPI-DOCKER-K8S\OCP\jenkins-shared-library
git status
git add README.md SETUP.md src vars
git commit -m "feat: central platformPipeline for cinehome, banking-demo, aiops"
git push -u origin main
```

Kiểm tra trên GitHub: thấy `vars/platformPipeline.groovy` và `src/com/platform/`.

---

## 2. Đăng ký Global Library trên Jenkins

URL lab: `https://jenkins-platform.apps.ocp01.npd.co`

### Cách A — UI (nhanh, ~2 phút)

1. **Manage Jenkins** → **System** (Configure System)
2. Kéo tới **Global Pipeline Libraries** → **Add**
3. Điền:

| Field | Value |
|-------|--------|
| **Name** | `platform` |
| **Default version** | `main` |
| **Load implicitly** | không bắt buộc |
| **Allow default version to be overridden** | ✓ bật |
| **Include @Library changes in job recent changes** | tuỳ chọn |
| **Retrieval method** | Modern SCM |
| **Source Code Management** | Git |
| **Project Repository** | `https://github.com/kevinram164/jenkins-shared-library.git` |
| **Credentials** | trống nếu public; nếu private → GitHub PAT (username + PAT) |
| **Library Path** | **để trống** (library nằm root repo, không phải subfolder) |
| **Behaviors** (nếu có) | Discover branches / tip of branch — mặc định ổn |

4. **Save**

### Cách B — JCasC (GitOps, bền)

Trong `jenkins.yaml` (Argo app platform-jenkins), thêm / thay script:

```yaml
platform-shared-library: |
  unclassified:
    globalLibraries:
      libraries:
        - name: "platform"
          defaultVersion: "main"
          retriever:
            modernSCM:
              libraryPath: ""
              scm:
                git:
                  remote: "https://github.com/kevinram164/jenkins-shared-library.git"
```

Sau sync Argo, kiểm tra lại UI vẫn thấy library `platform`.

> **Lưu ý:** JCasC `globalLibraries` thường **thay thế** list — nếu chỉ khai báo `platform` thì mất `cinehome`/`banking-demo`. Trong giai chuyển tiếp có thể liệt kê cả ba, hoặc chỉ `platform` sau khi mọi Jenkinsfile đã đổi.

### Kiểm tra library load được

Tạo job **Pipeline script** tạm:

```groovy
@Library('platform@main') _
node {
  echo "library ok"
  echo "has platformPipeline: ${this.metaClass.respondsTo(this, 'platformPipeline')}"
}
```

Hoặc chạy lại Multibranch của movie/banking/aiops — lỗi library sẽ hiện **ngay dòng đầu** log.

---

## 3. Jenkinsfile phía app (đã chuẩn bị sẵn)

| Repo | Branch job | `project` |
|------|------------|-----------|
| movie-web | `main` | `cinehome` |
| banking-demo | `dev-ocp` | `banking-demo` |
| Open-Source-AIOps-Platform | `main` | `aiops` |

Mẫu:

```groovy
@Library('platform@main') _

platformPipeline([
  project             : 'cinehome',
  harborHost          : 'harbor-platform.apps.ocp01.npd.co',
  // … vault / git overrides
  kanikoSkipTlsVerify : true,
])
```

**Commit + push** Jenkinsfile trên từng app repo (nếu chưa push).

Job Multibranch: **Scan Repository** / **Build with Parameters**.

---

## 4. Điều kiện runtime (tránh fail giữa chừng)

### 4.1 Agent Kaniko + SCC (OCP)

```bash
# SA + SCC (làm một lần)
oc get sa jenkins-kaniko -n platform
oc get scc jenkins-kaniko-root
# Nếu thiếu: chạy script trong movie-web hoặc banking phase9
# environments/dev-ocp/scripts/jenkins-kaniko-scc-setup.sh
```

Triệu chứng thiếu SCC: pod agent `CreateContainerConfigError` / bị deny `runAsUser: 0`.

### 4.2 Vault

Agent dùng SA `jenkins-kaniko` → Vault Kubernetes auth role `jenkins-kaniko`.

| Secret path | Keys | Dùng cho |
|-------------|------|----------|
| `cinehome/harbor` hoặc `platform/harbor` hoặc `aiops/harbor` | `username`, `password` | Kaniko push |
| `platform/github` | `username`, `pat` | git push GitOps |

Khớp với `vaultHarborPath` / `vaultGithubPath` trong Jenkinsfile.

Triệu chứng: log `Vault kubernetes login HTTP 403` hoặc `missing username/pat`.

### 4.3 Harbor

- Project tồn tại: `movie-web` / `banking-demo` / `aiops`
- Robot account trong Vault có quyền **Push**
- Host: `harbor-platform.apps.ocp01.npd.co`
- Lab thường cần `kanikoSkipTlsVerify: true`

### 4.4 Plugin Jenkins

Cần có sẵn (lab đã có nếu pipeline cũ chạy được):

- Kubernetes
- Pipeline
- Pipeline: Groovy Libraries
- Pipeline Utility Steps (`readJSON`)
- Git
- Durable Task (HEARTBEAT đã chỉnh trong javaOpts)

---

## 5. Cách chạy pipeline “không lỗi” lần đầu

### Bước khuyến nghị

1. Vào job Multibranch → branch đúng (`main` / `dev-ocp`)
2. **Build with Parameters**
3. Chọn **`BUILD_TARGET` = một service cụ thể**, ví dụ:
   - movie: `movie-api`
   - banking: `account-service`
   - aiops: `rca-agent`
4. Build → theo dõi stages:

```text
Checkout
Build <service>
Update GitOps
```

5. Verify:
   - Harbor có image `.../<project>/<service>:<sha7>`
   - Commit `ci: bump image tags…` trên repo (hoặc “GitOps values unchanged” nếu tag trùng)
   - Argo sync image mới

### Vì sao không để `auto` lần đầu?

`auto` chỉ build khi `git diff` chạm `watchPath`. Build thủ công / không có diff → pipeline **SUCCESS nhưng skip** — dễ tưởng “hỏng”. Lần đầu luôn dùng tên service hoặc `all`.

---

## 6. Bảng lỗi thường gặp → cách xử lý

| Log / triệu chứng | Nguyên nhân | Cách xử lý |
|-------------------|-------------|------------|
| `Could not find any definition of libraries [platform]` | Chưa đăng ký library / sai Name | §2 — Name đúng `platform` |
| `Library platform expected to contain…` / thiếu `vars` | Chưa push `vars/` lên GitHub hoặc **Library Path** sai | Path **trống**; kiểm tra GitHub có `vars/platformPipeline.groovy` |
| `ERROR: Could not find matching constructor` / compile Groovy | Syntax library / version cũ cache | **Scan** lại; Manage Jenkins → reload; build với `@Library('platform@main')` |
| `Vault kubernetes login HTTP 403` | Role/policy SA | Chạy lại vault-setup-jenkins-k8s-auth |
| `Vault … missing username` | Sai path secret | Sửa `vaultHarborPath` / ghi secret đúng |
| Kaniko `UNAUTHORIZED` Harbor | Robot không push được | Harbor project member + secret Vault |
| Pod agent pending / SCC | `jenkins-kaniko-root` | §4.1 |
| Stage FAIL exit `-1` sau `Pushed …@sha256` | JENKINS-48300 durable-task | Image **đã lên Harbor**; javaOpts HEARTBEAT đã set — chạy lại GitOps hoặc build lại nếu tag chưa bump |
| `Unknown BUILD_TARGET` | Gõ sai tên service | Xem catalog trong `Projects.groovy` |
| `Unknown project` | Sai `project:` | Chỉ: `cinehome` \| `banking-demo` \| `aiops` |
| GitOps sed không đổi tag | `helmKey` không khớp YAML | So `values-images*.yaml` với catalog |
| `git push` 403 | PAT thiếu `repo` | Vault `platform/github` PAT đúng quyền |

---

## 7. Migration an toàn (không gãy job cũ)

1. Push library + đăng ký `platform` (§1–2) — **giữ** library `cinehome`/`banking-demo` tạm.
2. Đổi + push Jenkinsfile từng repo → smoke 1 service.
3. Khi ổn: bỏ JCasC library cũ; README trong folder nhúng đã ghi *Moved*.
4. (Tuỳ chọn) Xóa thư mục `jenkins-shared-library/` trong app repo ở PR sau.

---

## 8. Thêm project mới sau này

1. Thêm entry trong `src/com/platform/Projects.groovy` (`services` + `defaults`).
2. Push library.
3. Jenkinsfile app:

```groovy
@Library('platform@main') _
platformPipeline([ project: 'ten-moi', /* overrides */ ])
```

Không cần tạo shared library riêng.

---

## 9. Liên kết

- Entry: `vars/platformPipeline.groovy`
- Catalog: `src/com/platform/Projects.groovy`
- README ngắn: [README.md](./README.md)
