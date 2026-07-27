package com.platform

class ChangeDetector implements Serializable {

    static List<String> buildTargetChoices(Map cfg) {
        def services = (cfg.services ?: [:]).findAll { k, v -> !v.optional }.keySet().sort() as List
        def optional = (cfg.services ?: [:]).findAll { k, v -> v.optional }.keySet().sort() as List
        return ['auto', 'all'] + services + optional
    }

    static List<String> resolve(def steps, Map cfg) {
        def services = cfg.services ?: [:]
        def required = services.findAll { k, v -> !v.optional }.keySet().sort() as List
        def allKnown = services.keySet().sort() as List

        if (steps.env.FORCE_BUILD_ALL == 'true') {
            steps.echo 'FORCE_BUILD_ALL=true — build required services'
            return required
        }

        def target = steps.params?.BUILD_TARGET ?: cfg.buildTarget ?: 'auto'
        steps.echo "BUILD_TARGET=${target}"

        if (target == 'all') {
            return required
        }
        if (target != 'auto') {
            if (!services.containsKey(target)) {
                steps.error("Unknown BUILD_TARGET: ${target}")
            }
            return [target]
        }

        return detectChanged(steps, cfg, allKnown)
    }

    private static List<String> detectChanged(def steps, Map cfg, List<String> all) {
        def services = cfg.services ?: [:]
        def changed = [] as Set
        try {
            def diff = steps.sh(
                script: "git diff --name-only HEAD~1 HEAD 2>/dev/null || git diff --name-only origin/${cfg.gitBranch}...HEAD",
                returnStdout: true,
            ).trim()
            if (!diff) {
                steps.echo 'auto: no diff — skip build. Use BUILD_TARGET=all or a service name.'
                return []
            }
            diff.split('\n').each { path ->
                def triggeredShared = false
                (cfg.sharedTriggers ?: []).each { trig ->
                    def pfx = trig.path ?: trig
                    if (path.startsWith(pfx.toString())) {
                        triggeredShared = true
                        def except = (trig.except ?: []) as List
                        services.each { name, meta ->
                            if (!meta.optional && !except.contains(name)) {
                                changed << name
                            }
                        }
                    }
                }
                if (triggeredShared) {
                    return
                }
                services.each { name, meta ->
                    if (meta.optional) {
                        return
                    }
                    def watch = meta.watchPath ?: meta.context
                    if (!watch || watch == '.') {
                        watch = meta.dockerfile?.replaceAll(/\/Dockerfile$/, '') ?: name
                        if (watch == 'Dockerfile' || !watch) {
                            watch = name
                        }
                    }
                    if (path.startsWith("${watch}/") || path == meta.dockerfile || path == "${watch}/Dockerfile") {
                        changed << name
                    }
                }
            }
        } catch (ignored) {
            steps.echo 'auto: change detection failed — skip build.'
            return []
        }
        if (changed.isEmpty()) {
            steps.echo 'auto: diff did not touch watched paths — skip build.'
        } else {
            steps.echo "auto: build ${changed.sort().join(', ')}"
        }
        return changed.sort() as List
    }
}
