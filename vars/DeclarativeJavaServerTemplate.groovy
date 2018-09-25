def call(body){
def config = [:]
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()
pipeline {
    agent {
        label 'worker'
    }
    tools {
        gradle 'GRADLE_LATEST'
    }
    stages{
       stage('Git Checkout'){
           steps{
               cleanWs()
               git credentialsId: '65bc32a1-9a81-446c-b37b-c0ac940783da', url: "${config.gitURL}", branch:"${config.branchName}"
           }
       }
       stage('Gradle Build'){
           steps {
              sh 'gradle clean build wrapper'
           }
       }
       stage('Deploy') {
           steps{
               script{
                   def jarName = sh returnStdout: true, script: "ls build/libs/|grep jar| head -1"
				   def userName = "jenkins"
                   jarName = jarName.trim()
                   sshagent(['faefb19b-7645-4251-a8bd-edd495ddc10d']) {
					   print "projectName: ${config.projectName}"
					   print "IPAddress: ${config.IPAddress}"
					   print "port: ${config.port}"
					   print "jarName: $jarName"
					   print "userName: $userName"
					   //sshCommand remote: remote, sudo:true, command: "mkdir -p /var/local/${config.projectName}"
					   sh "ssh $userName@${config.IPAddress} 'sudo mkdir -p /var/local/${config.projectName}'"
					   sh "ssh $userName@${config.IPAddress} 'sudo chmod 777 /var/local/${config.projectName}'"
                       sh "scp build/libs/$jarName $userName@${config.IPAddress}:/var/local/${config.projectName}"
                       try {
                           //sshCommand remote: remote, sudo:true, command: "fuser -k ${config.port}/tcp"
			   sh "ssh $userName@${config.IPAddress} 'sudo fuser -k ${config.port}/tcp'"
                       } catch (Exception e) {
                           print "No service running at port ${config.port}"
                       }
                       sleep(10)
                       sh "ssh $userName@${config.IPAddress} 'env SERVER.PORT=${config.port} nohup java -jar /var/local/${config.projectName}/$jarName </dev/null >/var/local/${config.projectName}/${config.projectName}.log 2>&1 & disown -h'"
                   }
               }
           }
       }
       stage('Automated Testing'){
           steps {
			   script {
			       if (config.automatatedTest) {
			           sh 'gradle automatedTests'
			       } else {
				       print "Automated tests were not run"
			       }
			   }
			   
           }
       }
       stage('Sonarqube Scan'){
           steps {
               withSonarQubeEnv('Sonarqube') {
                   // requires SonarQube Scanner for Gradle 2.1+
                   // It's important to add --info because of SONARJNKNS-281
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
    }
	post {
        success {
            script {
                if (config.successEmail) {
			print "success"
                    	// emailext body: 'Build success', subject: 'Jenkins test', to: "${config.emailAddress}"
                }
            }
        }
        failure {
		print "job failed"
		  //emailext body: 'Build failed', subject: 'Jenkins test', to: "${config.emailAddress}"
		}
	}
}
}
