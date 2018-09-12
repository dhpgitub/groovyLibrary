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
               git credentialsId: '65bc32a1-9a81-446c-b37b-c0ac940783da', url: 'https://github.com/dhpgitub/sonar-qube-test.git'
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
                   jarName = jarName.trim()
                   sshagent(['dc152500-562b-46c5-8097-e1ae443e967d']) {
                       sh "scp build/libs/$jarName statuser@137.117.85.216:."
                       try {
                           sshCommand remote: remote, sudo:true, command: "fuser -k 8081/tcp"
                       } catch (Exception e) {
                           print "No service running at port 8081"
                       }
                       sleep(10)
                       sh 'ssh statuser@137.117.85.216 "sudo env SERVER.PORT=8081 nohup java -jar /home/statuser/sonarqubeTest-0.0.1-SNAPSHOT.jar </dev/null >runserver.log 2>&1 & disown -h"'
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
                    sh "./gradlew --info Sonarqube -Dsonar.projectKey=sonar-qube-test -Dsonar.dependencyCheck.reportPath=${WORKSPACE}/build/reports/dependency-check-report.xml -Dsonar.projectName=sonar-qube-test"
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
