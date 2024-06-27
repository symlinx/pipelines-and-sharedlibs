import jenkins.model.*
import org.jenkinsci.plugins.workflow.cps.*
import org.jenkinsci.plugins.workflow.job.*
import org.jenkinsci.plugins.workflow.flow.*
import org.jenkinsci.plugins.workflow.cps.global.*
import jenkins.plugins.git.GitSCMSource
import java.nio.file.Paths
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever
import hudson.model.queue.QueueTaskDispatcher
import hudson.model.Queue
import hudson.model.queue.CauseOfBlockage
import hudson.model.Run
import hudson.FilePath
import hudson.model.TaskListener

def instance = Jenkins.getInstance()

try {
    // Ensure the shared library is available globally
    def globalLibraries = instance.getDescriptorByType(GlobalLibraries.class)
    def libraries = new ArrayList<>(globalLibraries.getLibraries())
    if (!libraries.find { it.name == 'pipelineAgentRouterLibrary' }) {
        def libraryConfiguration = new LibraryConfiguration('pipelineAgentRouterLibrary',
            new SCMSourceRetriever(new GitSCMSource(
                'pipeline-agent-router-lib',  // ID for the SCM source
                'git@github.com:symlinx/pipeline-agent-router.git', // Git repository URL
                'git_credential', // Jenkins credential ID for Git authentication
                '*',
                '',
                true)))
        libraryConfiguration.setDefaultVersion('main')
        libraryConfiguration.setImplicit(true)
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
                    CpsFlowDefinition definition = job.getDefinition() as CpsFlowDefinition

                    if (definition != null) {
                        String originalPipelineScript = definition.script
                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Intercepted job: ${job.name}")

                        // Retrieve and validate the shared library
                        def libRetriever = new SCMSourceRetriever(new GitSCMSource(
                            'pipeline-agent-router-lib',
                            'git@github.com:symlinx/pipeline-agent-router.git',
                            'git_credential',
                            '*',
                            '',
                            true
                        ))
                        // Use a dummy run and workspace for retrieval
                        def dummyRun = job.getLastBuild()
                        def dummyWorkspace = new FilePath(new File(instance.getRootDir(), "workspace/dummy"))
                        def dummyListener = TaskListener.NULL

                        libRetriever.retrieve("pipelineAgentRouterLibrary", "main", dummyWorkspace, dummyRun, dummyListener)

                        // Load the shared library and wrap the pipeline script
                        def libLoader = new GroovyShell().parse(new File(instance.getRootDir(), 'workflow-libs/pipelineAgentRouterLibrary/vars/pipelineAgentRouterLibrary.groovy'))
                        String modifiedPipelineScript = libLoader.call(originalPipelineScript)

                        definition.setScript(modifiedPipelineScript)
                    }
                }
            } catch (Exception e) {
                println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Error processing job: ${e.message}")
                e.printStackTrace()
            }
            return null  // Allow the job to run
        }
    }

    QueueTaskDispatcher.all().add(dispatcher)
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Added QueueTaskDispatcher to intercept pipeline jobs.")

} catch (Exception e) {
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Error in initialization script: ${e.message}")
    e.printStackTrace()
}
