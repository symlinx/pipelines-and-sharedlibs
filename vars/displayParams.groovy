// vars/displayParams.groovy

def call() {
    if (params) {
        echo "Pipeline Parameters:"
        params.each { key, value ->
            echo "${key}: ${value}"
        }
    } else {
        echo "No pipeline parameters provided."
    }
}
