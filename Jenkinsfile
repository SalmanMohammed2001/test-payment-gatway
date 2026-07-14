pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                git branch: 'staging',
                url: 'https://github.com/SalmanMohammed2001/test-payment-gatway.git',
                credentialsId: 'github-creds'
            }
        }



    }
}