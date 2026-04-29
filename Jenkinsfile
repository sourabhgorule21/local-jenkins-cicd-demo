pipeline {
    agent any

    environment {
        IMAGE_NAME = 'jenkins-cicd-demo'
        APP_CONTAINER_NAME = 'jenkins-cicd-demo-app'
        MYSQL_CONTAINER_NAME = 'jenkins-cicd-demo-mysql'
        APP_NETWORK = 'jenkins-cicd-demo-net'
        DB_HOST = 'jenkins-cicd-demo-mysql'
        DB_PORT = '3306'
        DB_NAME = 'appdb'
        DB_USERNAME = 'appuser'
        DB_PASSWORD = 'apppassword'
        MYSQL_ROOT_PASSWORD = 'rootpassword'
        APP_HOST_PORT = '9090'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Docker image') {
            steps {
                sh '''
                    docker build \
                      -t ${IMAGE_NAME}:${BUILD_NUMBER} \
                      -t ${IMAGE_NAME}:latest \
                      .
                '''
            }
        }

        stage('Stop old container (if exists)') {
            steps {
                sh '''
                    docker rm -f ${APP_CONTAINER_NAME} || true
                    docker rm -f ${MYSQL_CONTAINER_NAME} || true
                    docker network rm ${APP_NETWORK} || true
                '''
            }
        }

        stage('Run new container') {
            steps {
                sh '''
                    docker network create ${APP_NETWORK}

                    docker run -d \
                      --name ${MYSQL_CONTAINER_NAME} \
                      --network ${APP_NETWORK} \
                      -e MYSQL_DATABASE=${DB_NAME} \
                      -e MYSQL_USER=${DB_USERNAME} \
                      -e MYSQL_PASSWORD=${DB_PASSWORD} \
                      -e MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD} \
                      --health-cmd="mysqladmin ping -h localhost -uroot -p${MYSQL_ROOT_PASSWORD}" \
                      --health-interval=10s \
                      --health-timeout=5s \
                      --health-retries=20 \
                      mysql:8.4

                    echo "Waiting for MySQL init process to complete..."
                    for i in $(seq 1 60); do
                      if docker logs ${MYSQL_CONTAINER_NAME} 2>&1 | grep -q "MySQL init process done. Ready for start up"; then
                        break
                      fi
                      sleep 2
                    done
                    if ! docker logs ${MYSQL_CONTAINER_NAME} 2>&1 | grep -q "MySQL init process done. Ready for start up"; then
                      echo "MySQL init did not complete in time"
                      docker logs ${MYSQL_CONTAINER_NAME}
                      exit 1
                    fi

                    echo "Waiting for MySQL to become healthy..."
                    status=""
                    for i in $(seq 1 40); do
                      status=$(docker inspect --format='{{.State.Health.Status}}' ${MYSQL_CONTAINER_NAME})
                      if [ "$status" = "healthy" ]; then
                        break
                      fi
                      if [ "$status" = "unhealthy" ]; then
                        echo "MySQL is unhealthy"
                        docker logs ${MYSQL_CONTAINER_NAME}
                        exit 1
                      fi
                      sleep 3
                    done
                    if [ "$status" != "healthy" ]; then
                      echo "MySQL did not become healthy in time"
                      docker logs ${MYSQL_CONTAINER_NAME}
                      exit 1
                    fi

                    docker run -d \
                      --name ${APP_CONTAINER_NAME} \
                      --network ${APP_NETWORK} \
                      -e DB_HOST=${DB_HOST} \
                      -e DB_PORT=${DB_PORT} \
                      -e DB_NAME=${DB_NAME} \
                      -e DB_USERNAME=${DB_USERNAME} \
                      -e DB_PASSWORD=${DB_PASSWORD} \
                      -p ${APP_HOST_PORT}:9090 \
                      ${IMAGE_NAME}:latest

                    echo "Waiting for app startup..."
                    for i in $(seq 1 40); do
                      if docker logs ${APP_CONTAINER_NAME} 2>&1 | grep -q "Started CicdApplication"; then
                        break
                      fi
                      if [ "$(docker inspect --format='{{.State.Running}}' ${APP_CONTAINER_NAME})" != "true" ]; then
                        echo "App container exited early"
                        docker logs ${APP_CONTAINER_NAME}
                        exit 1
                      fi
                      sleep 3
                    done
                    if ! docker logs ${APP_CONTAINER_NAME} 2>&1 | grep -q "Started CicdApplication"; then
                      echo "App did not report startup in time"
                      docker logs ${APP_CONTAINER_NAME}
                      exit 1
                    fi
                '''
            }
        }

        stage('Show logs') {
            steps {
                sh '''
                    docker logs --tail 200 ${MYSQL_CONTAINER_NAME} || true
                    docker logs --tail 200 ${APP_CONTAINER_NAME}
                '''
            }
        }
    }
}
