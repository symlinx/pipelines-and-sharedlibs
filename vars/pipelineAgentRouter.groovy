// vars/pipelineAgentRouter.groovy
import hudson.model.TaskListener

def call(String pipelineScript) {
    return wrapPipeline(pipelineScript)
}

def wrapPipeline(String pipelineScript) {
    // Add logging with timestamp
    println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Original pipeline script:\n${pipelineScript}")

    // Check if the script is declarative or scripted
    if (isDeclarativePipeline(pipelineScript)) {
        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Detected declarative pipeline.")
        pipelineScript = modifyDeclarativePipeline(pipelineScript)
    } else {
        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Detected scripted pipeline. No modifications applied.")
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
        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Found docker block: ${dockerBlock}")

        def imageMatcher = (dockerBlock =~ imagePattern)
        if (imageMatcher.find()) {
            matched = true
            def dockerImage = imageMatcher.group(1)
            def label = getLabelForDockerImage(dockerImage)
            println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Transforming agent { docker { image '${dockerImage}' } } to agent { label '${label}' }")
            modifiedScript = modifiedScript.replaceFirst(matchPattern, "agent { label '${label}' }")
        }
    }

    if (!matched) {
        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] No matching agent { docker { image '...' } } block found. Running the original script.")
    } else {
        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Modified pipeline script:\n${modifiedScript}")
        validatePipelineScript(modifiedScript)
    }

    return modifiedScript
}

def getLabelForDockerImage(String dockerImage) {
    // Extract the latest string after the last /
    def imageName = dockerImage.tokenize('/').last()
    def label = "POD_${imageName}"
    println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Generated label '${label}' for docker image '${dockerImage}'")
    return label
}

def validatePipelineScript(String pipelineScript) {
    try {
        new GroovyShell().parse(pipelineScript)
        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Pipeline script validation passed.")
    } catch (Exception e) {
        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Pipeline script validation failed: ${e.message}")
        throw new IllegalArgumentException("Modified pipeline script is not valid: ${e.message}")
    }
}
