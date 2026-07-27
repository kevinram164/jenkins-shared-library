package com.platform

/**
 * Project catalogs — service maps + defaults for each app repo.
 * Jenkinsfile only passes harbor/vault/git overrides; services come from here
 * when config.project is set (or via thin wrappers cinehomePipeline / …).
 */
class Projects implements Serializable {

    static final Map CATALOG = [
        'cinehome': [
            defaults: [
                harborHost         : 'harbor-platform.apps.ocp01.npd.co',
                harborProject      : 'movie-web',
                gitBranch          : 'main',
                gitRepoUrl         : 'https://github.com/kevinram164/movie-web.git',
                gitopsValuesFile   : 'gitops/values-images.yaml',
                vaultHarborPath    : 'cinehome/harbor',
                vaultGithubPath    : 'platform/github',
                gitCommitEmail     : 'jenkins@cinehome.local',
                tagQuote           : true,
            ],
            services: [
                'movie-api': [
                    dockerfile  : 'Dockerfile',
                    context     : 'apps/movie-api',
                    helmKey     : 'movieApi',
                    watchPath   : 'apps/movie-api',
                    snapshotMode: 'full',
                ],
                'movie-web': [
                    dockerfile  : 'Dockerfile',
                    context     : 'phim-web-interface',
                    helmKey     : 'movieWeb',
                    watchPath   : 'phim-web-interface',
                    snapshotMode: 'time',
                ],
                'media-worker': [
                    dockerfile  : 'Dockerfile',
                    context     : 'apps/media-worker',
                    helmKey     : 'mediaWorker',
                    watchPath   : 'apps/media-worker',
                    snapshotMode: 'full',
                ],
            ],
        ],

        'banking-demo': [
            defaults: [
                harborHost         : 'harbor-platform.apps.ocp01.npd.co',
                harborProject      : 'banking-demo',
                gitBranch          : 'dev-ocp',
                gitRepoUrl         : 'https://github.com/kevinram164/banking-demo.git',
                gitopsValuesFile   : 'phase9-gitops-platform/gitops/values-images.yaml',
                vaultHarborPath    : 'platform/harbor',
                vaultGithubPath    : 'platform/github',
                gitCommitEmail     : 'jenkins@banking-demo.local',
                tagQuote           : false,
            ],
            sharedTriggers: [
                [path: 'phase8-application-v3/common/', except: ['frontend']],
            ],
            services: [
                'api-producer': [
                    dockerfile  : 'phase8-application-v3/producer/Dockerfile',
                    context     : '.',
                    helmKey     : 'api-producer',
                    snapshotMode: 'full',
                ],
                'auth-service': [
                    dockerfile  : 'phase8-application-v3/services/auth-service/Dockerfile',
                    context     : '.',
                    helmKey     : 'auth-service',
                    snapshotMode: 'full',
                ],
                'account-service': [
                    dockerfile  : 'phase8-application-v3/services/account-service/Dockerfile',
                    context     : '.',
                    helmKey     : 'account-service',
                    snapshotMode: 'full',
                ],
                'transfer-service': [
                    dockerfile  : 'phase8-application-v3/services/transfer-service/Dockerfile',
                    context     : '.',
                    helmKey     : 'transfer-service',
                    snapshotMode: 'full',
                ],
                'notification-service': [
                    dockerfile  : 'phase8-application-v3/services/notification-service/Dockerfile',
                    context     : '.',
                    helmKey     : 'notification-service',
                    snapshotMode: 'full',
                ],
                'frontend': [
                    dockerfile  : 'Dockerfile',
                    context     : 'frontend',
                    helmKey     : 'frontend',
                    watchPath   : 'frontend',
                    snapshotMode: 'time',
                ],
            ],
        ],

        'aiops': [
            defaults: [
                harborHost         : 'harbor-platform.apps.ocp01.npd.co',
                harborProject      : 'aiops',
                gitBranch          : 'main',
                gitRepoUrl         : 'https://github.com/kevinram164/Open-Source-AIOps-Platform.git',
                gitopsValuesFile   : 'gitops/values-images-incident-api.yaml',
                vaultHarborPath    : 'aiops/harbor',
                vaultGithubPath    : 'platform/github',
                gitCommitEmail     : 'jenkins@aiops.local',
                tagQuote           : true,
            ],
            services: [
                'incident-api': [
                    dockerfile       : 'Dockerfile',
                    context          : 'components/incident-api',
                    helmKey          : 'image',
                    watchPath        : 'components/incident-api',
                    snapshotMode     : 'full',
                    gitopsValuesFile : 'gitops/values-images-incident-api.yaml',
                ],
                'rca-agent': [
                    dockerfile       : 'Dockerfile',
                    context          : 'components/rca-agent',
                    helmKey          : 'image',
                    watchPath        : 'components/rca-agent',
                    snapshotMode     : 'full',
                    gitopsValuesFile : 'gitops/values-images-rca-agent.yaml',
                ],
                'remediation-controller': [
                    dockerfile       : 'Dockerfile',
                    context          : 'components/remediation-controller',
                    helmKey          : 'image',
                    watchPath        : 'components/remediation-controller',
                    snapshotMode     : 'full',
                    gitopsValuesFile : 'gitops/values-images-remediation-controller.yaml',
                ],
                'aiops-console': [
                    dockerfile       : 'Dockerfile',
                    context          : 'components/aiops-console',
                    helmKey          : 'image',
                    watchPath        : 'components/aiops-console',
                    snapshotMode     : 'full',
                    gitopsValuesFile : 'gitops/values-images-aiops-console.yaml',
                ],
            ],
        ],
    ]

    static Map get(String project) {
        def key = (project ?: '').trim()
        if (!CATALOG.containsKey(key)) {
            throw new IllegalArgumentException(
                "Unknown project '${key}'. Known: ${CATALOG.keySet().sort().join(', ')}"
            )
        }
        return CATALOG[key]
    }
}
