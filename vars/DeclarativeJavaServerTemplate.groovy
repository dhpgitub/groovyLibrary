def call(body){
def config = [:]
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()
pipeline {
    agent {
        label 'master'
    }
    tools {
        gradle 'GRADLE_LATEST'
    }
    stages{
       stage('Git Checkout'){
           steps{
               cleanWs()
               git credentialsId: '65bc32a1-9a81-446c-b37b-c0ac940783da', url: "${config.gitURL}"
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
				   def userName = "statuser"
                   jarName = jarName.trim()
                   sshagent(['dc152500-562b-46c5-8097-e1ae443e967d']) {
					   sh "sudo mkdir -p /var/local/${config.projectName}"
                       sh "scp build/libs/$jarName $userName@${config.IPAddress}:/var/local/${config.projectName}"
                       try {
                           sshCommand remote: remote, sudo:true, command: "fuser -k ${config.port}/tcp"
                       } catch (Exception e) {
                           print "No service running at port ${config.port}"
                       }
                       sleep(10)
                       sh "ssh $userName@${config.IPAddress} 'sudo env SERVER.PORT=${config.port} nohup java -jar /var/local/${config.projectName}/$jarName </dev/null >runserver.log 2>&1 & disown -h'"
                   }
               }
           }
       }
       stage('Automated Testing'){
           steps {
               sh 'gradle automatedTests'
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
}
}
