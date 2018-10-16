import groovy.json.*
@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}
def call(body){
    def config = [:]
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()
    def registryURL = "dhpcontainreg.azurecr.io"
    def tenableURL = "registry.cloud.tenable.com"
    String tenableApiUrl = " https://cloud.tenable.com"
    pipeline{
        agent {
            label 'worker'
        }
        tools {
            gradle 'GRADLE_LATEST'
        }
        stages{
            stage("Checkout Project"){
                steps{
                    print "${config.message}"
                    print "${config.gitURL}"
                    print "${config.projectName}"
                    print "${config.containerPort}"
                    print "${config.hostPort}"
                    print "${config.emailFlag}"
                    print "${config.emailAdd}"
                    print "${config.automatatedTest}"

                    cleanWs()
                    git credentialsId: '65bc32a1-9a81-446c-b37b-c0ac940783da', url: "${config.gitURL}"
                    stash includes: "", name: "gradle"
                }
            }
            // stage('build'){
            //     steps{
            //         sh 'gradle clean build wrapper'
            //         archiveArtifacts "build/libs/*.jar"
            //         sh "./gradlew dependencyCheckAnalyze"
            //         archiveArtifacts "build/reports/dependency-check-report.html"
            //     }
            // }
            stage('build docker image'){
                steps{
                    script{
                        docker.withRegistry("http://${registryURL}", "fd863aba-dd56-4c08-abd4-c6fcd9f4af57") {
                            def testImage = docker.build("${config.projectName}:${config.versionNum}")
                        }
                    }
                }
            }
            stage('run container'){
                steps{
                    script{
                        try{
                            sh "docker container rm -f ${config.projectName}"
                        }catch(Exception ex){}
                        sh "docker container run --name ${config.projectName} -d -p ${config.hostPort}:${config.containerPort} ${registryURL}/${config.repo}/${config.projectName}:${config.versionNum}"
                        // timeout(time: 30, unit: "SECONDS") {
                        //     waitUntil {
                        //         def r = sh script: "wget -q http://172.23.174.228:30012/swagger-ui.html -O /dev/null", returnStatus: true
                        //         return (r == 0);
                        //     }
                        // }
                    }
                }
            }
            stage('automated tests'){
                steps{
                    script {
                        if (config.automatatedTest) {
                            // sh 'gradle automatedTests'
                            // archiveArtifacts "build/reports/tests/test/*.html"
                        } else {
                            print "Automated tests were not run"
                        }
                    }
                }
            }
            stage("Sonar Scan"){
                steps{
                    sh "curl -u admin:admin -X POST 'http://52.179.212.186:9000/api/projects/create?key=ms-name-normalization&name=ms-name-normalization'"
                    script {
                        def scannerHome = tool 'SonarQube123';
                        withSonarQubeEnv('Sonarqube') {
                            sh "${scannerHome}/bin/sonar-scanner -X -Dsonar.projectKey=pytest -Dsonar.projectName=pytest -Dsonar.sources=."
                        }
                    }
                    // Remember the add webhook in sonarqube for the project - (Needs an additional project key setup)
                    timeout(time: 10, unit: "MINUTES") { 
                        script {
                            def qualitygate = waitForQualityGate()
                            if (qualitygate.status != "OK") {
                                error "Pipeline aborted due to quality gate failure: ${qualitygate.status}"
                            }
                        }
                    }
                }
            }
//           stage("Tenable scan") {
//		    steps{
//			    script {
//				    withCredentials([usernamePassword(credentialsId: '60b12faf-7ddb-49d4-aff9-e08c6a3ef1c1', passwordVariable: 'secret', usernameVariable: 'access')]) {
//                      sh "docker login -u $access -p $secret $tenableURL"
//                        sh "docker tag ${config.projectName}:${config.versionNum} ${tenableURL}/${config.projectName}:${config.versionNum}"
//                        def pushStdout = sh returnStdout:true, script:"docker push ${tenableURL}/${config.projectName}:${config.versionNum}"
//                        print('-----------------------PRINTING SHELL OUT--------------------------------')

//                        print pushStdout
                        // getting image sha

//                        String[] lines = pushStdout.split("\n")
//                        String lastLine = lines[lines.length - 1]
//                        String imageSha = (lastLine.split(" "))[2]
//                        imageSha = imageSha.substring(imageSha.lastIndexOf(":") + 1)

//                        print('-----------------------PRINTING IMAGE SHA OUT--------------------------------')
//                        print(imageSha)
                        // waiting for container security scans
//                        waitUntil{

//                            String method="container-security/api/v1/jobs/image_status_digest?image_digest="+imageSha.trim()
//                            def report = sh returnStdout:true, script:"curl -sH 'X-ApiKeys: accessKey=$access; secretKey=$secret' ${tenableApiUrl}/${method}"
//                            print("---------------------")
//                            print report

//                            def reportJson =  jsonParse(report)
//                            print(reportJson["job_status"])
//                            return(reportJson["job_status"]=="completed")
//                        }


                        //waiting for the compliance scan 

//                            method="container-security/api/v1/compliancebyname?image=${config.projectName}'&'repo=library'&'tag=${config.versionNum}"
//                            report = sh returnStdout:true, script:"curl -sH 'X-ApiKeys: accessKey=$access; secretKey=$secret' ${tenableApiUrl}/${method}"
//                            reportJson =  jsonParse(report)
//                            print(reportJson)
//                            print(reportJson["status"])
//                            if(reportJson["status"] != "pass"){
//                                method="container-security/api/v1/reports/by_image_digest?image_digest="+imageSha.trim()
//                                report = sh returnStdout:true, script:"curl -sH 'X-ApiKeys: accessKey=$access; secretKey=$secret' ${tenableApiUrl}/${method}"
//                                reportJson =  jsonParse(report)
//                                print(reportJson)
//                                error 'Compliance scan failed Please see the report below'
//                            }
//                }
//			}
//		}
//	}
            stage("Push to registry") {
                steps{
                    script {
                        docker.withRegistry("http://${registryURL}", "fd863aba-dd56-4c08-abd4-c6fcd9f4af57") {
			    docker.build("${registryURL}/${config.repo}/${config.projectName}:${config.versionNum}")
                            sh "docker push ${registryURL}/${config.repo}/${config.projectName}:${config.versionNum}"
                        }
                    }
                }
            }
            // stage('fortify'){
            //     agent{
            //         label "fortify"
            //     }
            //     steps{
            //         unstash "gradle"
            //         script{
            //             def gradle = "/opt/gradle/gradle-4.9-rc-1/bin/gradle"
            //             def fortLoc = "sourceanalyzer"
            //             sh "${fortLoc} -b Dbrunner -jdk 1.8 -gradle gradle clean build --stacktrace"
            //             sh "${fortLoc} -b Dbrunner -scan -f Dbrunner.fpr"

            //             archiveArtifacts "*.fpr"
            //             def url = "http://172.23.174.228:30010/job/test_jamie/job/test_dbRunner/${BUILD_NUMBER}/artifact/Dbrunner.fpr"
            //             def jobBuild = build job: "fortify_worknd_test", parameters: [string(name: "FileUrl", value:url), string(name: "ProjectName", value: "Dbrunner"), string(name: "Version", value: "1")]
            //             def jobResults = jobBuild.getResult()
            //             print "${jobResults}"
            //         }
            //     }
            // }
        }
         post{
                 success{
			sh "docker system prune -a"
                     script{
                        if (config.emailFlag) {
                            emailext body: 'Build success', subject: 'Jenkins test', to: "${config.emailAdd}"
                        }
                     }
                 }
                 failure{
                     emailext body: 'Build failed', subject: 'Jenkins test', to: "${config.emailAdd}"
                 }
            //  }
         }
    }
}
