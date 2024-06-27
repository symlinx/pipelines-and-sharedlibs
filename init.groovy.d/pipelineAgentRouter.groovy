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

def instance = Jenkins.getInstance()

try {
    // Define the shared library configuration once
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
                    def definition = job.getDefinition()

                    if (definition != null) {
                        String originalPipelineScript
                        boolean isScmFlowDefinition = false

                        if (definition instanceof CpsFlowDefinition) {
                            originalPipelineScript = definition.script
                        } else if (definition instanceof CpsScmFlowDefinition) {
                            isScmFlowDefinition = true
                            // Extract the pipeline script from the SCM
                            def scm = definition.getScm()
                            def workspace = new FilePath(new File(instance.getRootDir(), 'workspace/temp'))
                            def listener = TaskListener.NULL
                            scm.checkout(job, workspace, listener, null)
                            def scriptFile = workspace.child(definition.getScriptPath())
                            originalPipelineScript = scriptFile.readToString()
                        } else {
                            println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Unsupported job definition type: ${definition.getClass()}")
                            return null
                        }

                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Intercepted job: ${job.name}")

                        // Use LibraryAdder to load the shared library
                        LibraryAdder.get().addLibraries([libraryConfiguration], job.getLastBuild(), TaskListener.NULL)
                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Shared library retrieved successfully")

                        // Load the shared library and wrap the pipeline script
                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] [listener: pipelineAgentRouter] [Job: ${job.name}] Calling Shared library for pipeline script analysis/modification")
                        def libLoader = new GroovyShell().parse(new File(instance.getRootDir(), 'workflow-libs/pipelineAgentRouterLibrary/vars/pipelineAgentRouterLibrary.groovy'))
                        String modifiedPipelineScript = libLoader.call(originalPipelineScript, job.name)

                        // Execute the modified pipeline script dynamically
                        def script = new CpsFlowDefinition(modifiedPipelineScript, true)
                        job.setDefinition(script)
                        job.scheduleBuild2(0)
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
