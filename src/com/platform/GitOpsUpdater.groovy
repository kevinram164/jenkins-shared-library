package com.platform

class GitOpsUpdater implements Serializable {

    static void bumpImageTags(def steps, Map cfg, List<String> services) {
        def tag = GitRef.imageTag(steps)
        def quote = cfg.tagQuote != false
        def tagYaml = quote ? "\"${tag}\"" : tag
        def files = [] as Set

        services.each { svc ->
            def meta = (cfg.services ?: [:])[svc]
            def helmKey = meta.helmKey
            def file = meta.gitopsValuesFile ?: cfg.gitopsValuesFile
            files << file
            // Preserve existing indent of the tag: line under helmKey block
            steps.sh """
                set -e
                sed -i '/^${helmKey}:/,/^[^ ]/ s/^\\([[:space:]]*\\)tag: .*/\\1tag: ${tagYaml}/' ${file} || true
            """
        }

        def github = VaultClient.githubCredentials(steps, cfg)
        def email = cfg.gitCommitEmail ?: 'jenkins@platform.local'
        steps.withEnv([
            "GIT_USER=${github.username}",
            "GIT_TOKEN=${github.token}",
        ]) {
            steps.sh """
                set -e
                git config user.email "${email}"
                git config user.name "Jenkins CI"
                git add ${files.join(' ')}
                if git diff --cached --quiet; then
                  echo 'GitOps values unchanged'
                  exit 0
                fi
                git commit -m "ci: bump image tags to ${tag} [${services.join(', ')}]"
                export GIT_TERMINAL_PROMPT=0
                git push "https://x-access-token:\${GIT_TOKEN}@${cfg.gitRepoUrl.replaceFirst('^https://', '')}" HEAD:${cfg.gitBranch}
            """
        }
        steps.echo "Updated ${files} — ArgoCD will sync."
    }
}
