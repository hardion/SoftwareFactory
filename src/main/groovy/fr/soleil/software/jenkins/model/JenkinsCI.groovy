import fr.soleil.software.maven.model.MavenProject
import fr.soleil.software.scm.model.SCM


interface JenkinsCI{

  def getJob(String jobname)
  
  def getJobsView(String view)
  
  def scmTo(SCM scm, def job)
  
  def createMavenJob(MavenProject mvn, def templateName)
  
  def copyItem(def item, def copyName, def templateName)
  
  boolean support(def job)
  
  def createReportJobFromView(def view, def templatename, def update)
  
  def createLastReleaseJobFromView(def view, def templatename, def update)
  
  def changeSCMTrigger(def item, def config)
  
  def buildLinkBetweenHudsonAndMavenProjectName(def views)

  def updateJobDependencies(def updatingView, def depViews)
  
  def updateJobBuildTrigger(upstreamArtifactId, job, jobname)
  
  def clearJobBuildTrigger(def job)
  
  def rebuildAllJobOfView(def targetView)
  
  def printProjectInfo(def referenceViews)
  
  /**
   * Get SCM Object from the job definition
   */
  SCM scmFrom(def job)
  
  MavenProject from(def job)
    
  def getChildProjects(def job)
  
  def printlnImpact(def jobname, def pattern)
  
  def showLastLog(def job, def pattern)
  
  def recursiveImpact(def job, def pattern)
}