#!groovy
/**
 * Central CI entry — Kaniko → Harbor → bump GitOps values.
 *
 * Required: config.project ('cinehome'|'banking-demo'|'aiops')
 *        OR config.services (Map) for a new project without catalog entry.
 *
 * @param config harborHost, harborProject, gitBranch, gitRepoUrl, vault*, …
 */
def call(Map config = [:]) {
    def cfg = com.platform.PipelineConfig.mergeDefaults(config)
    setupParameters(cfg)

    podTemplate(
        yamlMergeStrategy: merge(),
        yaml: """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-kaniko
  containers:
    - name: jnlp
      env:
        - name: HOME
          value: /home/jenkins/agent
      workingDir: /home/jenkins/agent
      volumeMounts:
        - name: home-jenkins
          mountPath: /home/jenkins
    - name: kaniko
      image: ${cfg.kanikoImage}
      command: ["/busybox/busybox"]
      args:
        - "sh"
        - "-c"
        - "mkdir -p /home/jenkins/agent/bin && cp /busybox/busybox /home/jenkins/agent/bin/sh && cp /busybox/busybox /home/jenkins/agent/bin/busybox && exec /busybox/busybox sleep 99d"
      tty: true
      env:
        - name: PATH
          value: "/home/jenkins/agent/bin:/busybox:/kaniko:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
      securityContext:
        runAsUser: 0
        runAsGroup: 0
        runAsNonRoot: false
        allowPrivilegeEscalation: false
      volumeMounts:
        - name: home-jenkins
          mountPath: /home/jenkins
  volumes:
    - name: home-jenkins
      emptyDir: {}
""") {
        node(POD_LABEL) {
            stage('Checkout') {
                checkout scm
                env.GIT_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
            }

            def targets = com.platform.ChangeDetector.resolve(this, cfg)
            if (targets.isEmpty()) {
                echo 'No services selected — done.'
                currentBuild.result = 'SUCCESS'
                return
            }

            targets.each { svc ->
                stage("Build ${svc}") {
                    com.platform.KanikoBuilder.buildAndPush(this, cfg, svc)
                }
            }

            stage('Update GitOps') {
                com.platform.GitOpsUpdater.bumpImageTags(this, cfg, targets)
            }
        }
    }
}

def setupParameters(Map cfg) {
    properties([
        parameters([
            choice(
                name: 'BUILD_TARGET',
                choices: com.platform.ChangeDetector.buildTargetChoices(cfg),
                description: 'auto | all | <service>',
            ),
        ]),
    ])
}
