def IMAGE_VERSION = 'latest'
pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                credentialsId: 'GITHUB',
                url: 'https://github.com/CES4RFL/ExpressApp.git'
            }
        }
        
        
        stage('Test') {
            steps {
                sh 'npm install'
                sh 'NODE_OPTIONS=--experimental-vm-modules npx jest'
            }
        }
        
        stage('Build') {
            steps {
                
                script{
                    IMAGE_VERSION = sh(script: "node -p \"require('./package.json').version\"", returnStdout: true).trim()
                    echo "${IMAGE_VERSION}"
                }
                
                sh """  docker build -f Dockerfile -t public.ecr.aws/f0o1f7t2/appsnao:${IMAGE_VERSION} -t public.ecr.aws/f0o1f7t2/appsnao:latest .
                        docker images
                        docker system prune -a
                    """
            }
        }
        
        stage('Setup credentials') {
            steps {

                withCredentials([
                   string(credentialsId: 'aws_access_key_id', variable: 'aws_access_key_id'),
                   string(credentialsId: 'aws_secret_access_key', variable: 'aws_secret_access_key')
                ]){
                    sh """
                        #!/bin/bash
                        aws configure set aws_access_key_id $aws_access_key_id
                        aws configure set aws_secret_access_key $aws_secret_access_key
                        aws configure set default.region us-east-1 --output json --profile dev
                        aws configure list
                        aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws/f0o1f7t2
                    """
                }
            }
        } 
        
        stage('Docker push') {
            steps {
                sh """
         	      docker push public.ecr.aws/f0o1f7t2/appsnao:${IMAGE_VERSION}
                  docker push public.ecr.aws/f0o1f7t2/appsnao:latest 
                """
            }
        }


        stage('Deploy Image') {
            steps {
                sh """
         	      aws ecs update-service --cluster DevApp --service task-service --force-new-deployment
                """
            }
        }
    } 
    post { 
        always { 
            deleteDir()
            sh 'rm -r ~/.aws'
            script{
                def exitCode = sh(script:"docker rmi -f \$(docker images -aq)", returnStatus: true)
                if (exitCode != 0) {
                    echo "exit 0"
                }
            }
        }
    }
}