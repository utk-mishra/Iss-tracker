// Jenkins declarative pipeline for ISS Tracker
// Simplified version that works without tool or credential configuration
// Uses Docker containers for Maven and Node.js execution

pipeline {
    agent any

    environment {
        IMAGE_NAME = "iss-tracker"
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "✅ Checking out code from SCM..."
                checkout scm
            }
        }

        stage('Backend Tests') {
            steps {
                echo "🧪 Running backend tests with Maven..."
                sh '''
                    docker run --rm \
                      -v "$PWD":/workspace \
                      -w /workspace/backend \
                      maven:3.9-eclipse-temurin-21 \
                      mvn -B -q clean verify
                '''
            }
            post {
                always {
                    junit 'backend/target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('Frontend Tests') {
            steps {
                echo "🧪 Running frontend tests with Vitest..."
                sh '''
                    docker run --rm \
                      -v "$PWD":/workspace \
                      -w /workspace/frontend \
                      node:20-alpine \
                      sh -c "npm ci && npm test -- --run"
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest"
            }
        }

        stage('Security Scan') {
            steps {
                echo "🔒 Scanning image for vulnerabilities with Trivy..."
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      aquasec/trivy:latest image \
                      --exit-code 0 \
                      --severity HIGH,CRITICAL \
                      --format table \
                      ${IMAGE_NAME}:${IMAGE_TAG}
                '''
            }
        }

        stage('Smoke Test') {
            steps {
                echo "🔥 Running container smoke test..."
                script {
                    sh '''
                        # Start container
                        docker run -d --name iss-tracker-smoke-${BUILD_NUMBER} \
                          -p 8081:8080 ${IMAGE_NAME}:${IMAGE_TAG}
                        
                        # Wait for startup
                        echo "Waiting for application to start..."
                        sleep 20
                        
                        # Test health endpoint
                        echo "Testing health endpoint..."
                        curl -f http://localhost:8081/actuator/health || exit 1
                        
                        # Test API with offline mode
                        echo "Testing API endpoint..."
                        curl -f 'http://localhost:8081/api/v1/iss/overview?lat=0&lon=0&offline=true' || exit 1
                        
                        echo "✅ Smoke tests passed!"
                        
                        # Cleanup
                        docker stop iss-tracker-smoke-${BUILD_NUMBER}
                        docker rm iss-tracker-smoke-${BUILD_NUMBER}
                    '''
                }
            }
        }
    }

    post {
        always {
            echo "🧹 Cleaning up Docker images..."
            sh 'docker system prune -f || true'
        }
        success {
            echo "✅ Pipeline completed successfully!"
            echo "📦 Image: ${IMAGE_NAME}:${IMAGE_TAG}"
        }
        failure {
            echo "❌ Pipeline failed. Check logs above for errors."
        }
    }
}
