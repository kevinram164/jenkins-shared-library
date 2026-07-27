package com.platform

class PipelineConfig implements Serializable {

    static final Map PLATFORM_DEFAULTS = [
        harborHost         : 'harbor-platform.apps.ocp01.npd.co',
        kanikoImage        : 'gcr.io/kaniko-project/executor:v1.23.2-debug',
        kanikoSkipTlsVerify: true,
        kanikoUseCache     : false,
        vaultAddr          : 'http://vault.vault.svc.cluster.local:8200',
        vaultRole          : 'jenkins-kaniko',
        vaultHarborPath    : 'platform/harbor',
        vaultGithubPath    : 'platform/github',
        gitCommitEmail     : 'jenkins@platform.local',
        tagQuote           : true,
    ]

    /**
     * Merge order: PLATFORM_DEFAULTS ← project catalog defaults ← user config.
     * Ensures cfg.services and cfg.sharedTriggers are set.
     */
    static Map mergeDefaults(Map user) {
        def u = user ?: [:]
        def projectName = u.project as String
        Map projectDefaults = [:]
        Map services = (u.services instanceof Map) ? (u.services as Map) : null
        List sharedTriggers = (u.sharedTriggers instanceof List) ? (u.sharedTriggers as List) : null

        if (projectName) {
            def cat = Projects.get(projectName)
            projectDefaults = (cat.defaults ?: [:]) as Map
            if (services == null) {
                services = (cat.services ?: [:]) as Map
            }
            if (sharedTriggers == null) {
                sharedTriggers = (cat.sharedTriggers ?: []) as List
            }
        }

        if (services == null || services.isEmpty()) {
            throw new IllegalArgumentException(
                "platformPipeline requires config.project (cinehome|banking-demo|aiops) or config.services map"
            )
        }

        def merged = [:] + PLATFORM_DEFAULTS + projectDefaults + u
        merged.services = services
        merged.sharedTriggers = sharedTriggers ?: []
        return merged
    }
}
