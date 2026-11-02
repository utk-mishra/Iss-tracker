// Jenkins declarative pipeline for ISS Tracker (PROMPT.md section 11)
// Stages: checkout, backend tests, frontend tests, build image, Trivy scan, push, deploy
// Per-environment config via Spring profiles: dev/staging/prod

pipeline {
    agent any

    environment {
        // Docker registry and image name
        // NOTE: Configure 'docker-registry-url' credential in Jenkins
        DOCKER_REGISTRY = credentials('docker-registry-url')  // e.g., 'docker.io' or 'ghcr.io'
        IMAGE_NAME = "iss-tracker"
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        
        // Spring Boot profile per environment
        SPRING_PROFILE = "${env.DEPLOY_ENV ?: 'dev'}"
        
        // Docker image full name
        FULL_IMAGE_NAME = "${DOCKER_REGISTRY}/${IMAGE_NAME}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out code from SCM..."
                checkout scm
            }
        }

        stage('Backend Tests') {
            agent {
                docker {
                    image 'maven:3.9-eclipse-temurin-21'
                    args '-v $HOME/.m2:/root/.m2'
                    reuseNode true
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
                    reuseNode true
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
                    // Build main image
                    docker.build("${IMAGE_NAME}:${IMAGE_TAG}")
                    
                    // Tag as latest
                    sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest"
                    
                    // Tag with registry prefix for push
                    sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${FULL_IMAGE_NAME}:${IMAGE_TAG}"
                    sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${FULL_IMAGE_NAME}:latest"
                }
            }
        }

        stage('Security Scan') {
            steps {
                echo "Scanning image for vulnerabilities with Trivy..."
                script {
                    sh """
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                          aquasec/trivy:latest image \
                          --exit-code 0 \
                          --severity HIGH,CRITICAL \
                          --format table \
                          ${IMAGE_NAME}:${IMAGE_TAG}
                    """
                }
            }
        }

        stage('Smoke Test') {
            steps {
                echo "Running container smoke test..."
                script {
                    sh """
                        # Start container
                        docker run -d --name iss-tracker-smoke-${BUILD_NUMBER} \
                          -p 8081:8080 ${IMAGE_NAME}:${IMAGE_TAG}
                        
                        # Wait for startup
                        sleep 20
                        
                        # Test health endpoint
                        curl -f http://localhost:8081/actuator/health || exit 1
                        
                        # Test API with offline mode
                        curl -f 'http://localhost:8081/api/v1/iss/overview?lat=0&lon=0&offline=true' || exit 1
                        
                        # Cleanup
                        docker stop iss-tracker-smoke-${BUILD_NUMBER}
                        docker rm iss-tracker-smoke-${BUILD_NUMBER}
                    """
                }
            }
        }

        stage('Push to Registry') {
            when {
                branch 'main'
            }
            steps {
                echo "Pushing image to registry: ${FULL_IMAGE_NAME}"
                script {
                    // Check if credentials exist
                    try {
                        withCredentials([usernamePassword(
                            credentialsId: 'docker-registry-credentials',
                            usernameVariable: 'DOCKER_USER',
                            passwordVariable: 'DOCKER_PASS'
                        )]) {
                            echo "Logging in to ${DOCKER_REGISTRY}..."
                            sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin ${DOCKER_REGISTRY}"
                            
                            echo "Pushing ${FULL_IMAGE_NAME}:${IMAGE_TAG}..."
                            sh "docker push ${FULL_IMAGE_NAME}:${IMAGE_TAG}"
                            
                            echo "Pushing ${FULL_IMAGE_NAME}:latest..."
                            sh "docker push ${FULL_IMAGE_NAME}:latest"
                            
                            echo "✅ Image pushed successfully!"
                        }
                    } catch (Exception e) {
                        echo "⚠️ WARNING: Could not push to registry. Credential 'docker-registry-credentials' not found."
                        echo "   Configure credentials in Jenkins: Manage Jenkins → Credentials"
                        echo "   See SECRETS_SETUP.md for detailed instructions."
                        echo "   Continuing pipeline..."
                        currentBuild.result = 'UNSTABLE'
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
                    try {
                        // Get deployment host from environment variable
                        def deployHost = env."DEPLOY_HOST_${SPRING_PROFILE.toUpperCase()}"
                        
                        if (!deployHost) {
                            echo "⚠️ WARNING: DEPLOY_HOST_${SPRING_PROFILE.toUpperCase()} not set. Skipping deployment."
                            echo "   Configure in Jenkins: Manage Jenkins → Configure System → Global properties"
                            currentBuild.result = 'UNSTABLE'
                            return
                        }
                        
                        withCredentials([sshUserPrivateKey(
                            credentialsId: "deploy-${SPRING_PROFILE}-credentials",
                            keyFileVariable: 'SSH_KEY',
                            usernameVariable: 'SSH_USER'
                        )]) {
                            echo "Deploying to ${deployHost}..."
                            sh """
                                ssh -o StrictHostKeyChecking=no \
                                    -i \$SSH_KEY \$SSH_USER@${deployHost} \
                                    "docker pull ${FULL_IMAGE_NAME}:${IMAGE_TAG} && \
                                     docker stop iss-tracker-${SPRING_PROFILE} || true && \
                                     docker rm iss-tracker-${SPRING_PROFILE} || true && \
                                     docker run -d --name iss-tracker-${SPRING_PROFILE} \
                                       -p 8080:8080 \
                                       -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILE} \
                                       --restart unless-stopped \
                                       ${FULL_IMAGE_NAME}:${IMAGE_TAG}"
                            """
                            echo "✅ Deployed successfully to ${SPRING_PROFILE}!"
                        }
                    } catch (Exception e) {
                        echo "⚠️ WARNING: Deployment failed. Credential 'deploy-${SPRING_PROFILE}-credentials' not found."
                        echo "   Configure SSH credentials in Jenkins for deployment."
                        echo "   See SECRETS_SETUP.md for detailed instructions."
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }
    }

    post {
        always {
            echo "Cleaning up Docker images..."
            sh 'docker system prune -f || true'
        }
        success {
            echo "✅ Pipeline completed successfully!"
            echo "Image: ${FULL_IMAGE_NAME}:${IMAGE_TAG}"
        }
        unstable {
            echo "⚠️ Pipeline completed with warnings (check logs above)."
            echo "Most likely: registry credentials or deployment credentials not configured."
            echo "See SECRETS_SETUP.md for setup instructions."
        }
        failure {
            echo "❌ Pipeline failed. Check logs above for errors."
        }
    }
}
