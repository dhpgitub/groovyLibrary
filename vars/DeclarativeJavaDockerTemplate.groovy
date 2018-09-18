def call(body){
def config = [:]
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()
pipeline{
    agent any
    tools {
        gradle 'GRADLE_LATEST'
    }
    stages{
        stage("Checkout Project"){
            steps{
				print "${config.message}"
				print "${config.gitURL}"
				print "${config.projectName}"
				print "${config.portNumber}"
				print "${config.emailFlag}"
				print "${config.emailAdd}"
				print "${config.automatatedTest}"
				
                cleanWs()
                git credentialsId: '65bc32a1-9a81-446c-b37b-c0ac940783da', url: "${config.gitURL}"
                stash includes: "", name: "gradle"
            }
        }
        stage('build'){
            steps{
                sh 'gradle clean build wrapper'
                archiveArtifacts "build/libs/*.jar"
                sh "./gradlew dependencyCheckAnalyze"
                archiveArtifacts "build/reports/dependency-check-report.html"

            }
        }
		
        stage('build docker image'){
            steps{
                script{
                    docker.withRegistry("http://dhpcontainreg.azurecr.io", "fd863aba-dd56-4c08-abd4-c6fcd9f4af57") {
                        def testImage = docker.build("${config.repo}/${config.projectName}:${config.versionNum}")
                    }
                }
            }
        }
        stage('run container'){
            steps{
                script{
                    try{
                        sh "docker container rm -f ${config.projectName}"
                    }catch(Exception ex){
                    }
                    sh "docker container run --name ${config.projectName} -d -p ${config.portNumber}:9090 ${config.repo}/${config.projectName}:${config.versionNum}"
                    //timeout(time: 30, unit: "SECONDS") {
                    //    waitUntil {
                    //        def r = sh script: "wget -q http://172.23.174.228:30012/swagger-ui.html -O /dev/null", returnStatus: true
                    //        return (r == 0);
                    //    }
                    //}
                }
            }
        }
        stage('automated tests'){
            steps{
		script {
		       if (config.automatatedTest) {
			   sh 'gradle automatedTests'
			   archiveArtifacts "build/reports/tests/test/*.html"
		       } else {
			   print "Automated tests were not run"
		       }
	   	}
            }
        }
        stage("sonar scan"){
            steps{
                withSonarQubeEnv("sonarqube") {
                    sh "./gradlew --info Sonarqube -Dsonar.projectKey=${config.projectName} -Dsonar.dependencyCheck.reportPath=${WORKSPACE}/build/reports/dependency-check-report.xml -Dsonar.projectName=${config.projectName}"
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
	stage("Push to registry") {
		steps{
			script {
				docker.withRegistry("http://dhpcontainreg.azurecr.io", "fd863aba-dd56-4c08-abd4-c6fcd9f4af57") {
				sh "docker push ${config.repo}/${config.projectName}:${config.versionNum}"
				}
			}
		}
	}
        //stage('fortify'){
        //    agent{
        //        label "fortify"
        //    }
        //    steps{
        //        unstash "gradle"
        //        script{
        //            def gradle = "/opt/gradle/gradle-4.9-rc-1/bin/gradle"
        //            def fortLoc = "sourceanalyzer"
        //            sh "${fortLoc} -b Dbrunner -jdk 1.8 -gradle gradle clean build --stacktrace"
        //            sh "${fortLoc} -b Dbrunner -scan -f Dbrunner.fpr"

        //            archiveArtifacts "*.fpr"
        //            def url = "http://172.23.174.228:30010/job/test_jamie/job/test_dbRunner/${BUILD_NUMBER}/artifact/Dbrunner.fpr"
        //            def jobBuild = build job: "fortify_worknd_test", parameters: [string(name: "FileUrl", value:url), string(name: "ProjectName", value: "Dbrunner"), string(name: "Version", value: "1")]
        //            def jobResults = jobBuild.getResult()
        //            print "${jobResults}"
        //        }
        //    }
        //}
    }
    //post{
	//script{
    //    success{
    //        if (config.emailFlag) {
	//		emailext body: 'Build success', subject: 'Jenkins test', to: "${config.emailAdd}"
    //    }
	//	}
    //    failure{
    //        emailext body: 'Build failed', subject: 'Jenkins test', to: "${config.emailAdd}"

    //    }
    //}
	//}
	}
}
