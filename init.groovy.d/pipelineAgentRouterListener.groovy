import jenkins.model.*
import org.jenkinsci.plugins.workflow.cps.*
import org.jenkinsci.plugins.workflow.job.*
import org.jenkinsci.plugins.workflow.flow.*
import org.jenkinsci.plugins.workflow.cps.global.*

def instance = Jenkins.getInstance()

try {
    // Ensure the shared library is available globally
    def globalLibraries = instance.getDescriptorByType(GlobalLibraries.class).getLibraries()
    if (!globalLibraries.find { it.name == 'pipelineAgentRouterLibrary' }) {
        def libraryConfiguration = new LibraryConfiguration('pipelineAgentRouterLibrary',
            new SCMSourceRetriever(new BitbucketSCMSource(
                'bitbucket-credentials',  // Credential ID for Bitbucket
                'your-bitbucket-username',
                'pipelineAgentRouterLibrary',
                'master')))
        libraryConfiguration.setDefaultVersion('master')
        libraryConfiguration.setImplicit(true)
        globalLibraries.add(libraryConfiguration)
    }

    // Add a listener to intercept pipeline execution
    def listener = new QueueListener() {
        @Override
        void onEnterWaiting(Queue.WaitingItem item) {
            try {
                if (item.task instanceof WorkflowJob) {
                    WorkflowJob job = (WorkflowJob) item.task
                    CpsFlowDefinition definition = job.getDefinition() as CpsFlowDefinition

                    if (definition != null) {
                        String originalScript = definition.script
                        println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Intercepted job: ${job.name}")

                        // Load the shared library and wrap the pipeline script
                        def libLoader = new GroovyShell().parse(new File('/var/jenkins_home/workflow-libs/pipelineAgentRouterLibrary/vars/pipelineAgentRouter.groovy'))
                        String modifiedScript = libLoader.call(originalScript)

                        definition.setScript(modifiedScript)
                    }
                }
            } catch (Exception e) {
                println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Error processing job: ${e.message}")
            }
        }
    }

    Jenkins.instance.queue.addListener(listener)
    println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Added QueueListener to intercept pipeline jobs.")

} catch (Exception e) {
    println("[${new Date().format("yyyy-MM-dd HH:mm:ss")}] Error in initialization script: ${e.message}")
}
