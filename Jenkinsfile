// Jenkins declarative pipeline for ISS Tracker (PROMPT.md section 11)
// Stages: checkout, backend tests, frontend tests, build image, Trivy scan, push, deploy
// Per-environment config via Spring profiles: dev/staging/prod

pipeline {
    agent any

    environment {
        // Docker registry and image name (adjust to your registry)
        DOCKER_REGISTRY = credentials('docker-registry-url')
        IMAGE_NAME = "iss-tracker"
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        
        // Spring Boot profile per environment
        SPRING_PROFILE = "${env.DEPLOY_ENV ?: 'dev'}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out code..."
                checkout scm
            }
        }

        stage('Backend Tests') {
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-21'
                    args '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                echo "Running backend tests with Maven..."
                dir('backend') {
                    sh 'mvn -B -q clean verify'
                }
            }
            post {
                always {
                    junit 'backend/target/surefire-reports/**/*.xml'
                    publishHTML([
                        reportDir: 'backend/target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage Report',
                        allowMissing: true
                    ])
                }
            }
        }

        stage('Frontend Tests') {
            agent {
                docker {
                    image 'node:20-alpine'
                }
            }
            steps {
                echo "Running frontend tests with Vitest..."
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm test -- --run'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
                script {
                    docker.build("${IMAGE_NAME}:${IMAGE_TAG}")
                    docker.image("${IMAGE_NAME}:${IMAGE_TAG}").tag('latest')
                }
            }
        }

        stage('Security Scan') {
            steps {
                echo "Scanning image for vulnerabilities with Trivy..."
                script {
                    sh """
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:latest image --exit-code 0 --severity HIGH,CRITICAL \
                          --format table ${IMAGE_NAME}:${IMAGE_TAG}
                    """
                }
            }
        }

        stage('Push to Registry') {
            when {
                branch 'main'
            }
            steps {
                echo "Pushing image to registry..."
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'docker-registry-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin \${DOCKER_REGISTRY}"
                        sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} \${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
                        sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} \${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                        sh "docker push \${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
                        sh "docker push \${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                    }
                }
            }
        }

        stage('Deploy') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                echo "Deploying to ${SPRING_PROFILE} environment..."
                script {
                    withCredentials([usernamePassword(
                        credentialsId: "deploy-${SPRING_PROFILE}-credentials",
                        usernameVariable: 'DEPLOY_USER',
                        passwordVariable: 'DEPLOY_KEY'
                    )]) {
                        // Example deployment command (adjust to your infrastructure)
                        sh """
                            ssh -i \$DEPLOY_KEY \$DEPLOY_USER@\${DEPLOY_HOST_${SPRING_PROFILE.toUpperCase()}} \
                              "docker pull \${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} && \
                               docker stop iss-tracker-${SPRING_PROFILE} || true && \
                               docker rm iss-tracker-${SPRING_PROFILE} || true && \
                               docker run -d --name iss-tracker-${SPRING_PROFILE} \
                                 -p 8080:8080 \
                                 -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILE} \
                                 --restart unless-stopped \
                                 \${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            echo "Cleaning up..."
            sh 'docker system prune -f || true'
        }
        success {
            echo "✅ Pipeline completed successfully!"
        }
        failure {
            echo "❌ Pipeline failed. Check logs above."
        }
    }
}
