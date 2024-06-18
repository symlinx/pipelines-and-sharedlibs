// vars/pipelineAgentRouter.groovy
import hudson.model.TaskListener

def call(String pipelineScript) {
    return wrapPipeline(pipelineScript)
}

def wrapPipeline(String pipelineScript) {
    // Add logging
    println("Original pipeline script:\n${pipelineScript}")

    // Define a flexible pattern to match all docker agent blocks with any additional properties
    def matchPattern = /agent\s*\{\s*docker\s*\{([^\}]*)\}\s*\}/
    def imagePattern = /image\s*['"]([^'"]+)['"]/

    def matched = false
    def modifiedScript = pipelineScript

    // Find all matches and replace them
    def matcher = (pipelineScript =~ matchPattern)
    while (matcher.find()) {
        def dockerBlock = matcher.group(1)
        println("Found docker block: ${dockerBlock}")

        def imageMatcher = (dockerBlock =~ imagePattern)
        if (imageMatcher.find()) {
            matched = true
            def dockerImage = imageMatcher.group(1)
            def label = getLabelForDockerImage(dockerImage)
            println("Transforming agent { docker { image '${dockerImage}' } } to agent { label '${label}' }")
            modifiedScript = modifiedScript.replaceFirst(matchPattern, "agent { label '${label}' }")
        }
    }

    if (!matched) {
        println("No matching agent { docker { image '...' } } block found. Running the original script.")
    } else {
        println("Modified pipeline script:\n${modifiedScript}")
    }

    return modifiedScript
}

def getLabelForDockerImage(String dockerImage) {
    // Extract the latest string after the last /
    def imageName = dockerImage.tokenize('/').last()
    // Replace characters to form a valid label name
    def sanitizedImage = imageName.replaceAll(':', '_')
    def label = "GFS_${sanitizedImage}"
    println("We will switch to label '${label}' as user is requesting image '${dockerImage}'")
    return label
}
