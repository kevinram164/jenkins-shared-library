#!groovy
/** Compat wrapper — banking-demo. Prefer platformPipeline(project: 'banking-demo', …). */
def call(Map config = [:]) {
    platformPipeline([project: 'banking-demo'] + (config ?: [:]))
}
