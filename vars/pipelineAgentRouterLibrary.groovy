// vars/pipelineAgentRouterLibrary.groovy
import hudson.model.TaskListener

def call(String pipelineScript, String jobName) {
    return wrapPipeline(pipelineScript, jobName)
}

def wrapPipeline(String pipelineScript, String jobName) {
    // Add logging with timestamp
    logMessage("Original pipeline script:\n${pipelineScript}", jobName)

    // Check if the script is declarative or scripted
    if (isDeclarativePipeline(pipelineScript)) {
        logMessage("Detected declarative pipeline.", jobName)
        pipelineScript = modifyDeclarativePipeline(pipelineScript, jobName)
    } else {
        logMessage("Detected scripted pipeline. No modifications applied.", jobName)
    }

    return pipelineScript
}

def isDeclarativePipeline(String pipelineScript) {
    // Check for typical declarative pipeline keywords
    return pipelineScript.contains("pipeline {")
}

def modifyDeclarativePipeline(String pipelineScript, String jobName) {
    // Define a flexible pattern to match all docker agent blocks with any additional properties
    def matchPattern = /agent\s*\{\s*docker\s*\{([^\}]*)\}\s*\}/
    def imagePattern = /image\s*['"]([^'"]+)['"]/

    def matched = false
    def modifiedScript = pipelineScript

    // Find all matches and replace them
    def matcher = (pipelineScript =~ matchPattern)
    while (matcher.find()) {
        def dockerBlock = matcher.group(1)
        logMessage("Found docker block: ${dockerBlock}", jobName)

        def imageMatcher = (dockerBlock =~ imagePattern)
        if (imageMatcher.find()) {
            matched = true
            def dockerImage = imageMatcher.group(1)
            def label = getLabelForDockerImage(dockerImage, jobName)
            logMessage("Transforming agent { docker { image '${dockerImage}' } } to agent { label '${label}' }", jobName)
            modifiedScript = modifiedScript.replaceFirst(matchPattern, "agent { label '${label}' }")
        }
    }

    if (!matched) {
        logMessage("No matching docker agent. Running the original script.", jobName)
    } else {
        logMessage("Modified pipeline script:\n${modifiedScript}", jobName)
        validatePipelineScript(modifiedScript, jobName)
    }

    return modifiedScript
}

def getLabelForDockerImage(String dockerImage, String jobName) {
    // Extract the latest string after the last /
    def imageName = dockerImage.tokenize('/').last()
    // Replace ':' with '_'
    def sanitizedImageName = imageName.replace(':', '_')
    def label = "POD_${sanitizedImageName}"
    logMessage("Generated label '${label}' for docker image '${dockerImage}'", jobName)
    return label
}

def validatePipelineScript(String pipelineScript, String jobName) {
    try {
        new GroovyShell().parse(pipelineScript)
        logMessage("Pipeline script validation passed.", jobName)
    } catch (Exception e) {
        logMessage("Pipeline script validation failed: ${e.message}", jobName)
        throw new IllegalArgumentException("Modified pipeline script is not valid: ${e.message}")
    }
}

def logMessage(String message, String jobName) {
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [Job: ${jobName}] [SharedLib: pipelineAgentRouterLibrary] ${message}")
}
