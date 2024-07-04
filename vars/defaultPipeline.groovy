// vars/defaultPipeline.groovy

def call(Map config = [:]) {
    def param1 = config.get('exampleParam1', 'defaultValue1')
    def param2 = config.get('exampleParam2', 'defaultValue2')

    pipeline {
        agent none

        parameters {
            string(name: 'exampleParam1', defaultValue: param1, description: 'An example parameter 1')
            string(name: 'exampleParam2', defaultValue: param2, description: 'An example parameter 2')
        }

        stages {
            stage('Stage 1') {
                agent {
                    docker {
                        image 'registry1/tools/maven-3.8.0-openjdk11:1.2.3'
                        label 'agent1'
                        args '-v /var/run/docker.sock:/var/run/docker.sock'
                    }
                }
                steps {
                    script {
                        echo 'Running on agent1 with Docker (Maven 3.8.0, OpenJDK 11)'
                        displayParams()
                    }
                }
            }

            stage('Stage 2') {
                agent {
                    docker {
                        image 'registry3/tools/node-14.17.0:1.0.0'
                        label 'agent2'
                        args '-v /var/run/docker.sock:/var/run/docker.sock'
                    }
                }
                steps {
                    script {
                        echo 'Running on agent2 with Docker (Node.js 14.17.0)'
                        displayParams()
                    }
                }
            }

            stage('Stage 3') {
                agent {
                    docker {
                        image 'registry4/tools/python-3.8:1.1.1'
                        label 'agent3'
                        args '-v /var/run/docker.sock:/var/run/docker.sock'
                    }
                }
                steps {
                    script {
                        echo 'Running on agent3 with Docker (Python 3.8)'
                        displayParams()
                        sh '''
                            set -ex
                            hostname
                            id
                            pwd
                            env
                            ls -ltrh
                        '''
                    }
                }
            }
        }
    }
}
