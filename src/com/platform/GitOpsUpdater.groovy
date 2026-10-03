package com.platform

class GitOpsUpdater implements Serializable {

    static void bumpImageTags(def steps, Map cfg, List<String> services) {
        def tag = GitRef.imageTag(steps)
        def quote = cfg.tagQuote != false
        def tagYaml = quote ? "\"${tag}\"" : tag
        def files = [] as Set
        def edits = []

        services.each { svc ->
            def meta = (cfg.services ?: [:])[svc]
            def helmKey = meta.helmKey
            def file = meta.gitopsValuesFile ?: cfg.gitopsValuesFile
            files << file
            edits << [helmKey: helmKey, file: file]
        }

        def github = VaultClient.githubCredentials(steps, cfg)
        def email = cfg.gitCommitEmail ?: 'jenkins@platform.local'
        def gitopsRepo = cfg.gitopsRepoUrl ?: cfg.gitRepoUrl
        def gitopsBranch = cfg.gitopsBranch ?: cfg.gitBranch
        def separate = cfg.gitopsRepoUrl && cfg.gitopsRepoUrl != cfg.gitRepoUrl
        def work = separate ? '.gitops-bump' : '.'

        steps.withEnv([
            "GIT_USER=${github.username}",
            "GIT_TOKEN=${github.token}",
        ]) {
            if (separate) {
                def remote = gitopsRepo.replaceFirst('^https://', '')
                steps.sh """
                    set -e
                    rm -rf ${work}
                    git clone --branch ${gitopsBranch} --depth 1 \
                      "https://x-access-token:\${GIT_TOKEN}@${remote}" ${work}
                """
            }
            edits.each { edit ->
                def path = separate ? "${work}/${edit.file}" : edit.file
                steps.sh """
                    set -e
                    sed -i '/^${edit.helmKey}:/,/^[^ ]/ s/^\\([[:space:]]*\\)tag: .*/\\1tag: ${tagYaml}/' ${path} || true
                """
            }
            steps.sh """
                set -e
                cd ${work}
                git config user.email "${email}"
                git config user.name "Jenkins CI"
                git add ${files.join(' ')}
                if git diff --cached --quiet; then
                  echo 'GitOps values unchanged'
                  exit 0
                fi
                git commit -m "ci: bump image tags to ${tag} [${services.join(', ')}]"
                export GIT_TERMINAL_PROMPT=0
                git push "https://x-access-token:\${GIT_TOKEN}@${gitopsRepo.replaceFirst('^https://', '')}" HEAD:${gitopsBranch}
            """
        }
        steps.echo "Updated ${files} — ArgoCD will sync."
    }
}
