import jenkins.model.*
import org.jenkinsci.plugins.workflow.cps.*
import org.jenkinsci.plugins.workflow.job.*
import org.jenkinsci.plugins.workflow.flow.*
import org.jenkinsci.plugins.workflow.cps.global.*
import jenkins.plugins.git.GitSCMSource

def instance = Jenkins.getInstance()


// installs shared lib and listener for docker to kuberntes pipeline translation using labels

try {
    // Ensure the shared library is available globally
    def globalLibraries = instance.getDescriptorByType(GlobalLibraries.class).getLibraries()
    if (!globalLibraries.find { it.name == 'pipelineAgentRouterLibrary' }) {
        def libraryConfiguration = new LibraryConfiguration('pipelineAgentRouterLibrary',
            new SCMSourceRetriever(new GitSCMSource(
                'your-git-repo-url', // Git repository URL
                'git_credential', // Jenkins credential ID for Git authentication
                '*',
                '',
                true)))
        libraryConfiguration.setDefaultVersion('main')
        libraryConfiguration.setImplicit(true)
        globalLibraries.add(libraryConfiguration)
        println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Sharedlib pipelineAgentRouterLibrary created successfully on master")
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
            }
        }
    }

    Jenkins.instance.queue.addListener(listener)
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Added QueueListener to intercept pipeline jobs.")

} catch (Exception e) {
    println("[${new Date().format('yyyy-MM-dd HH:mm:ss')}] Error in initialization script: ${e.message}")
}
