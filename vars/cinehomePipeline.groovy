#!groovy
/** Compat wrapper — movie-web. Prefer platformPipeline(project: 'cinehome', …). */
def call(Map config = [:]) {
    platformPipeline([project: 'cinehome'] + (config ?: [:]))
}
