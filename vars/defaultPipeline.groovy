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
                    label 'agent1'
                }
                steps {
                    script {
                        echo 'Running on agent1'
                        displayParams()
                    }
                }
            }

            stage('Stage 2') {
                agent {
                    label 'agent2'
                }
                steps {
                    script {
                        echo 'Running on agent2'
                        displayParams()
                    }
                }
            }

            stage('Stage 3') {
                agent {
                    label 'agent3'
                }
                steps {
                    script {
                        echo 'Running on agent3'
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
