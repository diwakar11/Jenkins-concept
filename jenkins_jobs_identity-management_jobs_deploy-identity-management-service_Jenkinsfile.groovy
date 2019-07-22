SECURITY_PARAMS = ['IDENTITY_MANAGEMENT_PG_PASSWORD']

@NonCPS
def parseSingleParameters(params) {
    params.each { p ->
        try {
            paramValue = this."$p"
            if (paramValue != null && "" != paramValue.toString() && "null" != paramValue.toString()) {
                env."$p" = this."$p"
            }
        } catch (Exception e) {
            println "$p is not found as parameter, so skip it"
        }
    }
}

@NonCPS
def parseParameters(configuration) {
    org.yaml.snakeyaml.Yaml parser = new org.yaml.snakeyaml.Yaml()
    Map map = parser.load(configuration)
    map.each { k, v ->
        v.each { k2, v2 ->
            if (k != "identity_management_service") {
                def key = k.toUpperCase() + "_" + k2.toUpperCase()
                def value = v2.toString()
                if (value != "" && value != "null") {
                    println "Put to env key:value - ${key}:${value}"
                    env."$key" = "$value"
                }
            } else {
                def key = k2.toUpperCase()
                def value = v2.toString()
                if (value != "" && value != "null") {
                    println "Put to env key:value - ${key}:${value}"
                    env."$key" = "$value"
                }
            }
        }
    }
}

node {
    env.WORKSPACE = pwd()

    deleteDir()

    stage('Unzip manifest zip archive') {
        def unzip_command = "unzip ${env.JENKINS_HOME}/manifests/identity-management-service/${VERSION}/application.zip"
        sh unzip_command
    }

    stage('Export docker images from manifest') {
        load "${env.JENKINS_HOME}/manifests/identity-management-service/${VERSION}/env.properties"
        env.IDENTITY_MANAGEMENT_IMAGE_REPOSITORY = "${identity_management}"
        env.USER_MANAGEMENT_UI_IMAGE_REPOSITORY = "${user_management_ui}"
        if (binding.hasVariable('monitoring_agent')) {
            env.MONITORING_AGENT_IMAGE = "${monitoring_agent}"
        }
    }

    stage('Parse Identity management service configuration') {
        println "parse security parameters"
        parseSingleParameters(SECURITY_PARAMS)
        def configuration = "${PARAMETERS_YAML}"
        if (configuration != "") {
            println "Parse multiple parameter"
            parseParameters(configuration)
        } else {
            println "Parameters yaml not found!"
        }
    }


    stage('Deploy Identity management service') {
        wrap([$class: 'MaskPasswordsBuildWrapper']) { // enabling of the Jenkins "Mask Passwords" plugin
            sh "cd ./identity-management-service/identity-management-service && chmod +x *.sh && source ./setEnv.sh && ./install.sh"
        }
    }

    stage ('Starting identity-management-integration-tests') {
        if (env.RUN_SMOKE_TEST == "true") {
            PARAMETERS_YAML=
                    'OS_HOST: ' + env.OS_HOST + "\n" +
                    'OS_PROJECT: ' + env.OS_PROJECT + "\n" +
                    'OS_ADMINISTRATOR_NAME: ' + env.OS_ADMINISTRATOR_NAME + "\n" +
                    'OS_ADMINISTRATOR_PASSWORD: ' + env.OS_ADMINISTRATOR_PASSWORD  + "\n" +
                    'PG_USERNAME: ' + env.PG_USERNAME + "\n" +
                    'PG_PASSWORD: ' + env.PG_PASSWORD + "\n" +
                    'TAGS: ' + 'smoketest,ui-smoketest\n' +
                    'OS_TOKEN: ' + env.OS_TOKEN
            build job: 'identity-management-integration-tests',
                    parameters: [[$class: 'StringParameterValue', name: 'PARAMETERS_YAML', value: PARAMETERS_YAML], [$class: 'StringParameterValue', name: 'VERSION', value: env.VERSION]]
        }
    }
}
