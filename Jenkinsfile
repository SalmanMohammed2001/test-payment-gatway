pipeline {
    agent any

    tools {
        nodejs 'node20'
    }

    stages {
        stage('Test') {
            steps {
                sh 'npm ci'
                sh 'npm test'
            }
        }
    }
}
