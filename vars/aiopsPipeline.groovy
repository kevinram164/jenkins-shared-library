#!groovy
/** Compat wrapper — Open-Source-AIOps-Platform. Prefer platformPipeline(project: 'aiops', …). */
def call(Map config = [:]) {
    platformPipeline([project: 'aiops'] + (config ?: [:]))
}
