import jenkins.model.*
import org.jenkinsci.plugins.workflow.cps.*
import org.jenkinsci.plugins.workflow.job.*
import org.jenkinsci.plugins.workflow.flow.*
import org.jenkinsci.plugins.workflow.cps.global.*
import jenkins.plugins.git.GitSCMSource
import java.nio.file.Paths
import org.jenkinsci.plugins.workflow.libs.*
import hudson.model.queue.QueueTaskDispatcher
import hudson.model.Queue
import hudson.model.queue.CauseOfBlockage
import hudson.FilePath
import hudson.model.TaskListener
import hudson.model.Run
import hudson.plugins.git.GitSCM
import hudson.Launcher
import hudson.scm.SCM
import hudson.scm.SCMRevisionState
import hudson.Extension

class PipelineAgentRouterAction extends InvisibleAction {}

def instance = Jenkins.getInstance()

try {
    // Define the shared library configuration
    def libraryConfiguration = new LibraryConfiguration('pipelineAgentRouterLibrary',
        new SCMSourceRetriever(new GitSCMSource(
            'pipeline-agent-router-lib',  // ID for the SCM source
            'git@github.com:symlinx/pipeline-agent-router.git', // Git repository URL
            'git_credential', // Jenkins credential ID for Git authentication
            '*', // includes
            '', // excludes
            true // ignoreOnPushNotifications
        )))
    libraryConfiguration.setDefaultVersion('main')
    libraryConfiguration.setImplicit(true)

    // Ensure the shared library is available globally
    def globalLibraries = instance.getDescriptorByType(GlobalLibraries.class)
    def libraries = new ArrayList<>(globalLibraries.getLibraries())
    if (!libraries.find { it.name == 'pipelineAgentRouterLibrary' }) {
        libraries.add(libraryConfiguration)
        globalLibraries.setLibraries(libraries)
        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Created Sharedlib pipelineAgentRouterLibrary on master")
    }

    // Add a dispatcher to intercept pipeline execution
    def dispatcher = new QueueTaskDispatcher() {
        @Override
        CauseOfBlockage canRun(Queue.Item item) {
            try {
                if (item.task instanceof WorkflowJob) {
                    WorkflowJob job = (WorkflowJob) item.task

                    // Check if the job is already modified to prevent infinite loop
                    def modified = job.getLastBuild()?.getAction(PipelineAgentRouterAction) != null

                    if (modified) {
                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Already modified, allowing the job to run.")
                        return null // Allow the job to run
                    }

                    def definition = job.getDefinition()
                    if (definition != null) {
                        String originalPipelineScript
                        SCM scmDefinition

                        if (definition instanceof CpsFlowDefinition) {
                            originalPipelineScript = definition.script
                        } else if (definition instanceof CpsScmFlowDefinition) {
                            // Extract the pipeline script from the SCM
                            scmDefinition = definition.getScm()
                            Run<?, ?> lastBuild = job.getLastBuild()
                            if (scmDefinition instanceof GitSCM && lastBuild != null) {
                                FilePath workspace = new FilePath(new File(instance.getRootDir(), 'workspace/temp'))
                                TaskListener listener = TaskListener.NULL
                                Launcher launcher = instance.createLauncher(listener)
                                File changelogFile = File.createTempFile("changelog", ".txt")
                                scmDefinition.checkout(lastBuild, launcher, workspace, listener, changelogFile, SCMRevisionState.NONE)
                                FilePath scriptFile = workspace.child(definition.getScriptPath())
                                originalPipelineScript = scriptFile.readToString()
                            } else {
                                println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Unsupported SCM type or missing last build.")
                                return null
                            }
                        } else {
                            println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Unsupported job definition type: ${definition.getClass()}")
                            return null
                        }

                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Intercepted job: ${job.name}")

                        // Use LibraryRetriever to load the shared library
                        def retriever = libraryConfiguration.getRetriever()
                        def workspaceLib = new FilePath(new File(Jenkins.instance.getRootDir(), 'workflow-libs/pipelineAgentRouterLibrary'))
                        def dummyRun = job.getLastBuild()
                        def dummyListener = TaskListener.NULL

                        retriever.retrieve("pipelineAgentRouterLibrary", "main", workspaceLib, dummyRun, dummyListener)
                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Shared library retrieved successfully")

                        // Load the shared library and wrap the pipeline script
                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Calling Shared library for pipeline script analysis/modification")
                        def libLoader = new GroovyShell().parse(new File(instance.getRootDir(), 'workflow-libs/pipelineAgentRouterLibrary/vars/pipelineAgentRouterLibrary.groovy'))
                        String modifiedPipelineScript = libLoader.call(originalPipelineScript, job.name)

                        // Set the modified pipeline script to the job definition
                        job.setDefinition(new CpsFlowDefinition(modifiedPipelineScript, true))

                        // Mark the job as modified
                        job.addAction(new PipelineAgentRouterAction())

                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Job script modified.")
                    }
                }
            } catch (Exception e) {
                println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${item.task.name}] Error processing job: ${e.message}")
                e.printStackTrace()
            }
            return null  // Allow the job to run
        }
    }

    QueueTaskDispatcher.all().add(dispatcher)
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] Added QueueTaskDispatcher to intercept pipeline jobs.")

} catch (Exception e) {
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] Error in initialization script: ${e.message}")
    e.printStackTrace()
}
