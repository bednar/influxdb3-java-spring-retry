#!/bin/zsh

# This script sets up a simple K8S environment with minikube.
# It assumes that

SCRIPT_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
SPRING_RETRY_IMAGE=${SPRING_RETRY_IMAGE:-spring-retry:test}
MINIKUBE_IMG_CTX=${MINIKUBE_IMG_CTX:-"docker.io/library"}
SPRING_RETRY_DEPLOYMENT_YAML=${SPRING_RETRY_DEPLOYMENT_YAML:-"${SCRIPT_DIR}/spring-retry-depl.yaml"}
SPRING_RETRY_SERVICE_YAML=${SPRING_RETRY_SERVICE_YAML:-"${SCRIPT_DIR}/spring-retry-svc.yaml"}

help() {
  printf "\nThis script sets up a simple K8S environment with minikube and docker.\n"
  printf "It assumes that a docker image for the spring-retry application has already\n"
  printf "been loaded into local docker.\n\n"
  printf "e.g.\n\n"
  printf "   docker build -tag \"spring-retry:test\" .\n\n"
  printf "Flags:\n\n"
  printf "   -c|--cleanup  tears down the environment and deletes related k8s resources.\n"
  printf "   -?|-h|--help  prints this message.\n\n"
  printf "Without flags the environment will be started.\n\n"
}

verify_env() {
  printf "Verifying environment.\n"
  if ! command -v docker &> /dev/null
  then
    printf "docker could not be found.  It is required to run this script.\n"
    exit 1
  fi

  printf "found docker.\n"

  if ! command -v minikube &> /dev/null
  then
    printf "minikube could not be found.  It is required to run this script.\n"
    exit 1
  fi

  printf "found minikube.\n"

  if ! command -v kubectl &> /dev/null
  then
    printf "kubectl could not be found.  It is required to run this script.\n"
    exit 1
  fi

  printf "found kubectl.\n"

  if [ -z "$(docker images -q ${SPRING_RETRY_IMAGE} 2> /dev/null)" ]; then
    print "failed to find ${SPRING_RETRY_IMAGE} in local docker.\n"
  fi

  printf "found ${SPRING_RETRY_IMAGE}.\n"

  if [ ! -e "${SPRING_RETRY_DEPLOYMENT_YAML}" ]; then
    printf "Deployment yaml ${SPRING_RETRY_DEPLOYMENT_YAML} not found.  Exiting.\n"
    exit 1
  fi

  printf "found ${SPRING_RETRY_DEPLOYMENT_YAML}.\n"

  if [ ! -e "${SPRING_RETRY_SERVICE_YAML}" ]; then
    printf "Service yaml ${SPRING_RETRY_SERVICE_YAML} not found.  Exiting.\n"
    exit 1
  fi

  printf "found ${SPRING_RETRY_SERVICE_YAML}.\n"
}

start_minikube() {
  print "starting minikube.\n"

  if ! minikube start; then
    printf "failed to start minikube.  Exiting.\n"
    exit 1
  fi

  print "minikube started.\n"
}

verify_app_image() {
  if [ -z "$(minikube image ls | grep ${MINIKUBE_IMG_CTX}/${SPRING_RETRY_IMAGE} 2> /dev/null)" ]; then
    printf "${SPRING_RETRY_IMAGE} not found in minikube.  Loading it...\n"
    if ! minikube image load ${SPRING_RETRY_IMAGE}; then
      printf "failed to load ${SPRING_RETRY_IMAGE} to minikube.  Exiting.\n"
    fi
    printf "${SPRING_RETRY_IMAGE} loaded.\n"
  else
    printf "Found ${MINIKUBE_IMG_CTX}/${SPRING_RETRY_IMAGE} in minikube.\n"
  fi
}

deploy_app() {
  print "Deploying Spring-Retry deployment.\n"

  if ! kubectl apply -f ${SPRING_RETRY_DEPLOYMENT_YAML}; then
    printf "failed to deploy Spring-Retry deployment.  Exiting\n"
    # TODO cleanup before exit
    exit 1
  fi

  print "Deploying Spring-Retry service.\n"

  if ! kubectl apply -f ${SPRING_RETRY_SERVICE_YAML}; then
    printf "failed to deploy Spring-Retry service.  Exiting\n"
    # TODO cleanup before exit
    exit 1
  fi
}

start_forwarding() {
  POD_NAME=$(kubectl get pods | grep "spring-retry" | awk '{print $1}')

  while [[ "${POD_NAME}" == "" ]]
  do
    sleep 1
    POD_NAME=$(kubectl get pods | grep "spring-retry" | awk '{print $1}')
  done

  POD_STATUS=$(kubectl get pods ${POD_NAME} --no-headers=true | awk '{print $3}')

  until [[ "${POD_STATUS}" == "Running" ]]
  do
    sleep 1
    POD_STATUS=$(kubectl get pods ${POD_NAME} --no-headers=true | awk '{print $3}')
    printf "${POD_NAME} status: ${POD_STATUS}\n"
  done

  kubectl port-forward service/spring-retry-svc 8080:8080 > /dev/null 2>&1 &

  echo $! > ${SCRIPT_DIR}/forwarding.pid

}

start_all() {
  printf "starting all.\n"
  verify_env
  start_minikube
  verify_app_image
  deploy_app
  sleep 1
  start_forwarding
  POD_NAME=$(kubectl get pods | grep "spring-retry" | awk '{print $1}')
  printf "Application spring-retry running in pod ${POD_NAME}.\n"
  printf "Follow the spring-retry log with: kubectl logs -f pod/${POD_NAME}\n"
}

cleanup() {
  printf "cleaning up.\n"
  FORWARDING_PID=$(cat ${SCRIPT_DIR}/forwarding.pid)
  if ! kill ${FORWARDING_PID}; then
    printf "Failed to kill port forwarding process ${FORWARDING_PID}.  It may still be running with a different ID.\n"
  else
    printf "Port forwarding process terminated.\n"
  fi
  kubectl delete service/spring-retry-svc
  kubectl delete deployment/spring-retry
  sleep 1
  minikube stop
}

if [[ $# -eq 0 ]]; then
  start_all
else
  while [[ $# -gt 0 ]]; do
    case $1 in
       -c|--cleanup)
         cleanup
         shift
         ;;
       -?|-h|--help)
        help
        exit 0
        ;;
      *)
         print "unknown argument ${1}"
         shift
         ;;
    esac
  done
fi
