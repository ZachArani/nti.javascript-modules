def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node {
        try {
            println "${WORKSPACE}"
            stage ('Clone') {
                checkout scm
                def gitURL = scm.getUserRemoteConfigs()[0].getUrl().replace("git@github.com:","https://github.com/").replace(".git","");
                sh "echo $gitURL"
                script {
                    properties([[$class: 'GithubProjectProperty',
                    projectUrlStr: gitURL]])
                }
            }
            stage ('Clean') {
                sh "npx @nti/ci-scripts clean"
            }
            if(params.createTag == null || params.createTag == '' || "${BRANCH_NAME}" != "master") {
                stage ('Prepare') {
                    sh "npx @nti/ci-scripts prepare"
                }
           }
           stage ('Install') {
               if(params.createTag == null || params.createTag == '') {
                   sh "npx @nti/ci-scripts install"
               }
               else {
                    sh "npx @nti/ci-scripts install-strict"
               }
           }
            stage("Run") {
                if((params.createTag != null && params.createTag != '') || "${BRANCH_NAME}" == "master") {
                    sh "npx @nti/ci-scripts publish"
                }
                else {
                    sh "npx @nti/ci-scripts pack"
                }
            }
        } catch (err) {
         //   currentBuild.result = 'FAILED'
            def testCase = err instanceof hudson.AbortException;
            sh "echo ${testCase}"
            if(currentBuild.result == 'ABORTED')
                sh "echo abort"
            if(env.BRANCH_NAME == "master"){
                step([$class: 'GitHubIssueNotifier',
                      issueAppend: true,
                      issueLabel: '',
                      issueTitle: '$JOB_NAME $BUILD_DISPLAY_NAME failed'])
            }
            throw err
        }
    parameters {
      string(name: 'createTag', defaultValue: '', description: '')
      booleanParam(name: 'buildAsSnapshot', defaultValue: false)
    }
  }
}
