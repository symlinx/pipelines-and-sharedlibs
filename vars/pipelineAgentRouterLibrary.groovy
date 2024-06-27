// vars/pipelineAgentRouterLibrary.groovy
import hudson.model.TaskListener

def call(String pipelineScript) {
    return wrapPipeline(pipelineScript)
}

def wrapPipeline(String pipelineScript) {
    // Add logging with timestamp
    logMessage("Original pipeline script:\n${pipelineScript}")

    // Check if the script is declarative or scripted
    if (isDeclarativePipeline(pipelineScript)) {
        logMessage("Detected declarative pipeline.")
        pipelineScript = modifyDeclarativePipeline(pipelineScript)
    } else {
        logMessage("Detected scripted pipeline. No modifications applied.")
    }

    return pipelineScript
}

def isDeclarativePipeline(String pipelineScript) {
    // Check for typical declarative pipeline keywords
    return pipelineScript.contains("pipeline {")
}

def modifyDeclarativePipeline(String pipelineScript) {
    // Define a flexible pattern to match all docker agent blocks with any additional properties
    def matchPattern = /agent\s*\{\s*docker\s*\{([^\}]*)\}\s*\}/
    def imagePattern = /image\s*['"]([^'"]+)['"]/

    def matched = false
    def modifiedScript = pipelineScript

    // Find all matches and replace them
    def matcher = (pipelineScript =~ matchPattern)
    while (matcher.find()) {
        def dockerBlock = matcher.group(1)
        logMessage("Found docker block: ${dockerBlock}")

        def imageMatcher = (dockerBlock =~ imagePattern)
        if (imageMatcher.find()) {
            matched = true
            def dockerImage = imageMatcher.group(1)
            def label = getLabelForDockerImage(dockerImage)
            logMessage("Transforming agent { docker { image '${dockerImage}' } } to agent { label '${label}' }")
            modifiedScript = modifiedScript.replaceFirst(matchPattern, "agent { label '${label}' }")
        }
    }

    if (!matched) {
        logMessage("No matching docker agent. Running the original script.")
    } else {
        logMessage("Modified pipeline script:\n${modifiedScript}")
        validatePipelineScript(modifiedScript)
    }

    return modifiedScript
}

def getLabelForDockerImage(String dockerImage) {
    // Extract the latest string after the last /
    def imageName = dockerImage.tokenize('/').last()
    // Replace ':' with '_'
    def sanitizedImageName = imageName.replace(':', '_')
    def label = "GFS_${sanitizedImageName}"
    logMessage("Switching pipeline build to label '${label}' for docker image '${dockerImage}'")
    return label
}

def validatePipelineScript(String pipelineScript) {
    try {
        new GroovyShell().parse(pipelineScript)
        logMessage("Pipeline script validation passed.")
    } catch (Exception e) {
        logMessage("Pipeline script validation failed: ${e.message}")
        throw new IllegalArgumentException("Modified pipeline script is not valid: ${e.message}")
    }
}

def logMessage(String message) {
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [SharedLib: pipelineAgentRouterLibrary] [Job: ${env.JOB_NAME}] ${message}")
}