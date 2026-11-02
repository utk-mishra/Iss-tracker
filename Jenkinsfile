// Jenkins declarative pipeline for ISS Tracker.
// Maven and Node run in throwaway Docker containers, so the Jenkins server needs only
// Git and access to the Docker socket (no tool or credential configuration).
// Assumes Jenkins was started with: -v jenkins_home:/var/jenkins_home
//                                    -v /var/run/docker.sock:/var/run/docker.sock

pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        IMAGE_NAME   = "iss-tracker"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        JENKINS_VOL  = "jenkins_home"          // must match the volume name used for the Jenkins container
        SMOKE_NAME   = "iss-smoke-${env.BUILD_NUMBER}"
        SMOKE_NET    = "iss-net-${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out code from SCM..."
                checkout scm
            }
        }

        stage('Backend Tests') {
            steps {
                echo "Running backend tests with Maven..."
                sh '''
                    docker run --rm \
                      -u "$(id -u):$(id -g)" \
                      -v "${JENKINS_VOL}":/var/jenkins_home \
                      -w "$WORKSPACE/backend" \
                      maven:3.9-eclipse-temurin-21 \
                      mvn -B -q clean verify -Dmaven.repo.local=/var/jenkins_home/.m2-ci/repository
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('Frontend Tests and Build') {
            steps {
                echo "Running frontend tests with Vitest..."
                sh '''
                    docker run --rm \
                      -u "$(id -u):$(id -g)" \
                      -e HOME=/tmp -e npm_config_cache=/tmp/.npm \
                      -v "${JENKINS_VOL}":/var/jenkins_home \
                      -w "$WORKSPACE/frontend" \
                      node:20-alpine \
                      sh -c "npm ci && npm test -- --run && npm run build"
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
                sh 'docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest .'
            }
        }

        stage('Security Scan') {
            steps {
                echo "Scanning image for vulnerabilities with Trivy (report only)..."
                // Pin this tag to a specific Trivy release that you have checked.
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v trivy-cache:/root/.cache \
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
                echo "Running container smoke test on a private network..."
                sh '''
                    docker network create ${SMOKE_NET}
                    docker run -d --name ${SMOKE_NAME} --network ${SMOKE_NET} ${IMAGE_NAME}:${IMAGE_TAG}

                    ok=0
                    for i in $(seq 1 30); do
                      if docker run --rm --network ${SMOKE_NET} curlimages/curl -fs http://${SMOKE_NAME}:8080/actuator/health >/dev/null; then
                        ok=1; break
                      fi
                      sleep 2
                    done
                    [ "$ok" = "1" ] || { echo "Health check never passed"; docker logs ${SMOKE_NAME} | tail -50; exit 1; }

                    docker run --rm --network ${SMOKE_NET} curlimages/curl -fs \
                      "http://${SMOKE_NAME}:8080/api/v1/iss/overview?lat=0&lon=0&offline=true" >/dev/null
                    echo "Smoke tests passed"
                '''
            }
        }
    }

    post {
        always {
            // Remove only what this pipeline created. Never prune the shared Docker daemon.
            sh 'docker rm -f ${SMOKE_NAME} 2>/dev/null || true'
            sh 'docker network rm ${SMOKE_NET} 2>/dev/null || true'
        }
        success {
            echo "Pipeline completed successfully. Image: ${IMAGE_NAME}:${IMAGE_TAG}"
        }
        failure {
            echo "Pipeline failed. Check the stage logs above."
        }
    }
}