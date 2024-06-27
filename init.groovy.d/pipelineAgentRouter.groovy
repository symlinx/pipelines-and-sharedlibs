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

def instance = Jenkins.getInstance()


// installs shared lib and listener for docker to kuberntees pipeline translation using labels

try {
    // Ensure the shared library is available globally
    def globalLibraries = instance.getDescriptorByType(GlobalLibraries.class).getLibraries()
    if (!globalLibraries.find { it.name == 'pipelineAgentRouterLibrary' }) {
        def libraryConfiguration = new LibraryConfiguration('pipelineAgentRouterLibrary',
            new SCMSourceRetriever(new GitSCMSource(
                'pipeline-agent-router-lib',  // ID for the SCM source
                'https://bitbucket.org/your-username/pipelineAgentRouterLibrary.git', // Git repository URL
                'git_credential', // Jenkins credential ID for Git authentication
                '*',
                '',
                true)))
        libraryConfiguration.setDefaultVersion('master')
        libraryConfiguration.setImplicit(true)
        globalLibraries.add(libraryConfiguration)
        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Created Sharedlib pipelineAgentRouterLibrary on master")
    }

    // Add a dispatcher to intercept pipeline execution
    def dispatcher = new QueueTaskDispatcher() {
        @Override
        boolean canRun(Queue.Item item) {
            try {
                if (item.task instanceof WorkflowJob) {
                    WorkflowJob job = (WorkflowJob) item.task
                    CpsFlowDefinition definition = job.getDefinition() as CpsFlowDefinition

                    if (definition != null) {
                        String originalPipelineScript = definition.script
                        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Intercepted job: ${job.name}")

                        // Check if the shared library is available
                        def libDir = Paths.get(Jenkins.instance.getRootDir().absolutePath, 'workflow-libs', 'pipelineAgentRouterLibrary', 'vars')
                        if (!libDir.toFile().exists()) {
                            throw new IllegalStateException("Shared library not found in expected path: ${libDir}")
                        }

                        // Load the shared library and wrap the pipeline script
                        def libLoader = new GroovyShell().parse(new File(libDir.toFile(), 'pipelineAgentRouter.groovy'))
                        String modifiedPipelineScript = libLoader.call(originalPipelineScript)

                        definition.setScript(modifiedPipelineScript)
                    }
                }
            } catch (Exception e) {
                println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Error processing job: ${e.message}")
                e.printStackTrace()
            }
            return true
        }
    }

    QueueTaskDispatcher.all().add(dispatcher)
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Added QueueTaskDispatcher to intercept pipeline jobs.")

} catch (Exception e) {
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Error in initialization script: ${e.message}")
    e.printStackTrace()
}
