package fr.soleil.software.jenkins.model

import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.scm.model.CVS
import fr.soleil.software.scm.model.SubversionJava

import fr.soleil.software.maven.model.MavenProject
import fr.soleil.software.ica.IcaProxy

import groovy.xml.StreamingMarkupBuilder

import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.URLENC

import org.slf4j.*

// Get Job from Jenkins server
class JenkinsREST /*implements JenkinsCI*/{
  
  Logger log = LoggerFactory.getLogger(JenkinsREST.class)
  StreamingMarkupBuilder outputBuilder = new StreamingMarkupBuilder()
  XmlSlurper xml = new XmlSlurper()
  RESTClient jenkins
  
  // DogWatch for recursive analyze of dependencies
  def dogWatch = 20 // MAGIC NUMBER (Here at Soleil, we don't have more than X level of dependency ... maybe not
  
  def url
  
  public static final void main(String[] args){
    def ci = new JenkinsREST("http://calypso/jenkins/".toURL())

    IcaProxy.configureProxy()
    //ci.createReportJobFromView("test", "template.report.cpp")
    //ci.printlnImpact("fr.soleil.lib.Monochromator_NAR_RELEASE",".*ERROR.*")
    //ci.printlnImpact("fr.soleil.lib.Monochromator_NAR_RELEASE",".*ERROR.*")
    ci.jobNames.findAll{ it.text() ==~ /.*_JAR_RELEASE/}.each { jobname -> 
      ci.showLastLog(jobname.text(), ".*ERROR.*")
    }
    //def item = ci.getItem("Archiving.AdbArchivingServers")
    //item.displayName = "fr.soleil.deviceservers.adbArchivingServers_JAR_HEAD"
    //ci.updateItem("Archiving.AdbArchivingServers", item)
    //ci.printProjectInfo("CPP Libraries, CPP Devices, Java Applications, Java Libraries, Java Devices")
    //    ci.createReleaseJobFromView("Java Libraries", "template.release.java", true)
    //    ci.updateJobDependencies("CPP Libraries_RELEASE")
    
    //    ci.createReleaseJobFromView("CPP Devices", "template.release.cpp.test")
    //    ci.updateJobDependencies("CPP Libraries_RELEASE", ["CPP Libraries_RELEASE"])
    //def job = ci.getJob("fr.soleil.lib.SFTest")
    //println job
    //def test =  ci.createItem("fr.soleil.lib.SFTest-TEST", "template.java")
    //println test
    //ci.deleteJob("fr.soleil.lib.SFTest2")
    //println "Recreate : " + ci.createItem("fr.soleil.lib.SFTest-TEST", "template.java")
    
  }
  
  JenkinsREST(def url){
    this.url = url
    jenkins = new RESTClient(url)

  }
  private URL query(def entity){
    new URI(scheme, userInfo, host, port, "$rootpath/$entity",query, fragment ).toURL()
  }
  
  def existJob(String jobname){
    def result = false
    try { // expect an exception from a 404 response:
      def resp = jenkins.get( path: "job/$jobname/config.xml")
      
      if(resp.success){
        result= true
        log.debug("Response : Get Job $jobname")
      }else{
        log.error("Response error : can't get job ${resp.data}")
      }
    }catch( ex ) {
      log.debug( "This job doesn't exists : "+ex.response.status )
    }
    return result
  }

  def getJobNames(){
    def result
    def resp = jenkins.get( path: "api/xml")
      
    if(resp.success){
      result = resp.data.'**'.findAll{ it.name()=="job" }.collect(){ it.name }

      log.debug("Response : Get All Jobs")
    }else{
      log.error("Response error : can't get job ${resp.data}")
    }
      
    return result
  }
  
  def getJob(String jobname){
    // Automatic parsing with XMLSlurper through the RESTClient request
    def result
    def resp = jenkins.get( path: "job/$jobname/config.xml")
      
    if(resp.success){
      result = resp.data
      log.debug("Response : Get Job $jobname")
    }else{
      log.error("Response error : can't get job ${resp.data}")
    }
      
    return result
  }
  
  def getItem(String jobname){
    // Automatic parsing with XMLSlurper through the RESTClient request
    def result
    def resp = jenkins.get( path: "job/$jobname/api/xml")
      
    if(resp.success){
      result = resp.data
      log.debug("Response : Get Item $jobname")
    }else{
      log.error("Response error : can't get Item ${resp.data}")
    }
      
    return result
  }
  
  def getBuild(String jobname, String buildnumber){
    // Automatic parsing with XMLSlurper through the RESTClient request
    def result
    def resp = jenkins.get( path: "job/$jobname/$buildnumber/api/xml")
      
    if(resp.success){
      result = resp.data
      log.debug("Response : Get Build $buildnumber of $jobname")
    }else{
      log.error("Response error : can't get Build $buildnumber ${resp.data}")
    }
      
    return result
    
  }

  def deleteJob(String jobname){
    if(existJob(jobname)){
      def resp = jenkins.post(path:"job/$jobname/doDelete"  )
      
      if(resp.success){
        log.debug("Response : Deleted")
      }else{
        log.error("Response error : can't delete ${resp.data}")
      }
    }
  }
  
  def createItem(def name, def templateName){
    def result
    
    if(!this.existJob(name)){
      def postBody = ["name":name,"mode":"copy","from":templateName] // will be url-encoded
      def resp = jenkins.post(path:'createItem', body:postBody, requestContentType : URLENC  )

      if(resp.success){
        result = getJob(name)
        log.debug("Response : Create new Item $name")
      }else{
        log.error("Response error : can't create new Item ${resp.data}")
      }
    }else{
      result = this.getJob(name)
    }
    
    return result
  }
  
  private def getJobURL(def jobname){
    "$url/job/$jobname/config.xml"
  }
  
  
  private String toString(def node){
    outputBuilder.bind{ mkp.yield node }
  }
  
  private def toXmlNode(def node){
    xml.parseText(this.toString(node))
  }

  

  def updateJob(String jobname, def job){
    
    if(this.existJob(jobname)){
      // check the whole document using XmlUnit
      String result = this.toString(job)

      try{
        def resp = jenkins.post(path:"job/$jobname/config.xml", body:result, requestContentType:"text/xml")

        if(resp.status == 200){
          log.debug("Response : update Job $jobname")
        }else{
          log.error("Response error : update Job ${resp.data}")
        }
        
      }catch( e ){
        log.error("Response exception :  ${this.toString(e.response.data)}")
      }
    }

  }
  
  def updateItem(String jobname, def item){
    
    //    if(this.existJob(jobname)){
    // check the whole document using XmlUnit
    String result = this.toString(item)

    def resp = jenkins.post(path:"job/$jobname/api/xml", body:result, requestContentType:"text/xml")

    if(resp.status == 200){
      log.debug("Response : update Item $jobname")
    }else{
      log.error("Response error : update Item ${resp.data}")
    }
    //    }

  }

  def isJobEnabled(def jobname){ this.getItem(jobname).buildable.text()=="true" }

  def enableJob(def jobname){
    if(existJob(jobname)){
      def resp = jenkins.post(path:"job/$jobname/enable"  )
      if(resp.success){
        log.debug("Response : Enabled $jobname")
      }else{
        log.error("Response error : can't enable this job ${resp.data}")
      }
    }
  }
  
  def disableJob(def jobname){
    if(existJob(jobname)){
      def resp = jenkins.post(path:"job/$jobname/disable"  )
      if(resp.success){
        log.debug("Response : Disabled $jobname")
      }else{
        log.error("Response error : can't enable this job ${resp.data}")
      }
    }
  }
  
  
  def getView(String view){
    def result
    if(this.existView(view)){
      def resp = jenkins.get(path:"view/$view/api/xml")
    
      if(resp.success){
        result = resp.data
        log.debug("Response : get view $view")
      }else{
        log.error("Response error : can't get this view ${resp.data}")
      }
    }
    return result
  }
  
  def updateView(String viewname, def view){
    String result
    if(this.existView(viewname)){
      // check the whole document using XmlUnit
      def outputBuilder = new StreamingMarkupBuilder()
      result = outputBuilder.bind{ mkp.yield view }

      def resp = jenkins.post(path:"view/$viewname/api/xml", body:result, requestContentType:"text/xml")

      if(resp.status == 200){
        log.debug("Response : update View $viewname")
      }else{
        log.error("Response error : update View ${resp.data}")
      }
    }
    return result

  }

  def existView(String viewname){
    def result = false
    try { // expect an exception from a 404 response:
      def resp = jenkins.get(path:"view/$viewname/api/xml")

      if(resp.success){
        result = true
        log.debug("Response : get view $viewname")
      }else{
        log.error("Response error : can't get this view ${resp.data}")
      }
    }catch( ex ) {
      log.debug( "This view doesn't exists : $viewname "+ex.response.status )
    }
    return result
  }


  /**
   *
   *<listView>
   *  <job>
   *    <name>4. Generate Passerelle ROOT</name>
   *    <url>http://calypso/jenkins/job/4.%20Generate%20Passerelle%20ROOT/</url>
   *    <color>blue</color>
   *    </job>
   *  <job>
   *    <name>...
   */
  def getJobsView(String view){
    def result = this.getView(view).'**'.findAll{
      it.name()=="job"
    }
    if(!result){
      log.error("Response error : can't get the jobs of this view ${result.data}")
    }
    return result
  }

  def addJobToView( def view, def jobname){
    view << {
      job{
        name( jobname )
        url( (this.getJobURL(jobname)-"/config.xml")+"/")
        color("grey")
      }
    }
  }
  
  /**
   * Get SCM Object from the job definition
   * 
   * Copy from HudsonCI (groovysh implementation)
   */
  SCM getScmConfiguration(def job){
  
    def root=""
    def module=""
    def tag=""
  
    switch(job.scm.@"class"){
    case "hudson.scm.CVSSCM" :
      root=job.scm.cvsroot.text()
      module=job.scm.module.text()
      tag=job.scm.branch.text()
      break
    
    case "hudson.scm.SubversionSCM" :
      log.debug("hudson.scm.SubversionSCM ${job.scm.locations[0]}")
      root = job.scm.locations[0]."hudson.scm.SubversionSCM_-ModuleLocation".remote.text()
      break
    
    default:
      log.error("Other SCM than CVS and SVN are not supported (job=job;job.scm=${job.scm.@"class"})")
    }
    log.debug("root=$root;module=$module;tag=$tag")
    ScmBuilder.create(root, module,tag)
  }

  def buildLinkBetweenHudsonAndMavenProjectName(def view){
    def result = [
      jobs:[:],
      mvn:[:]
    ]
    
    log.debug("Please wait, the request to get all project's configuration may take a while")
    
    this.getJobsView(view).each(){
      if(this.isJobEnabled(it.name.text())){
        def job = this.getJob(it.name.text())

        if( this.support(job)){
          def scm = this.getScmConfiguration(job)
          def mvn = new MavenProject(scm)
          result.jobs[it.name.text()]=[job:job, mvn:mvn]
          result.mvn[mvn.simpleArtifactId]=[jobname:it.name.text(), job:job, mvn:mvn]

        }else{
          log.warn("This job is not supported")
        }
      }
    }
    log.debug("Map Jenkins-Maven built")
    
    return result
  }
  
  MavenProject from(def job){

    MavenProject mvn = null
    def scm = this.getScmConfiguration(job)
    if(scm){
      mvn = new MavenProject(scm)
    }
    
    return mvn
  }
 
  // These following method are not yet available
  
  def setScmConfiguration(def job, SCM scm){

    switch(job.scm.@"class"){
    case "hudson.scm.CVSSCM" && scm.provider == SCM.ScmType.cvs:
      job.scm.replaceBody {
        cvsroot(scm.root)
        module(scm.module)
        if(scm.branch){
          branch( scm.branch )
          isTag( scm.isTag()?"true":"false" )
        }
      }

      break
    
    case "hudson.scm.SubversionSCM" && scm.provider == SCM.ScmType.svn :
      log.debug("[hudson.scm.SubversionSCM] ${job.scm.locations[0]}")
      job.scm.locations[0]."hudson.scm.SubversionSCM_-ModuleLocation".remote = scm.devURL
      break
      
    default: // Include "hudson.scm.NullSCM"
      job.scm.replaceNode ( this.createScmConfiguration (scm))
    }
  }
  
  def createScmConfiguration (SCM newscm){
    Closure result
    switch(newscm.class){
    case CVS :
      result = {
        scm ("class":"hudson.scm.CVSSCM"){
          cvsroot( newscm.root )
          module ( newscm.module )
          if(newscm.branch){
            branch ( newscm.branch )
            isTag ( newscm.isTag()?"true":"false" )
          }
        }
      }
      break;
    case SubversionJava :
      result = {
        scm ("class":"hudson.scm.SubversionSCM"){
          locations{
            "hudson.scm.SubversionSCM_-ModuleLocation"{
              remote(newscm.devURL)
            }
          }
        }
      }
    
      break;

    default:
      log.error("Other SCM than CVS and SVN are not supported (job=$job;job.scm=${job.scm.@"class"})")    
    }
    return result
  }
  
  def createMavenJob(def newjobname, MavenProject mvn, def templateName){
    
    log.debug("createMavenJob(${mvn}, ${templateName})")
     
    //    if(this.existJob(newjobname)){
    //      this.deleteJob(newjobname)
    //    }
    if(!this.existJob(newjobname)){
      def newjob = this.createItem(newjobname, templateName)
      
      this.setScmConfiguration(newjob, mvn.scm)
      
      log.debug("${newjobname} (packaging=${mvn.packaging}; os=${mvn.os})")
      if(mvn.packaging == "nar"){
        this.setMatrixConfiguration(newjob,{
          "hudson.matrix.LabelAxis"{
              name("label")
              values{
                if(mvn.os == "any" || mvn.os=="Linux"){
                  string("rhel4")
                }
                if(mvn.os == "any" || mvn.os=="Windows"){
                  string("windows")
                }
              }
            }
          })
      }
      
      

      updateJob(newjobname, newjob)
      enableJob(newjobname)
      
    }else{
      log.warn("This project already exists :  $newjobname")
    }

    return newjobname
  }

  private def getMatrixConfiguration(def job){
    // Special configuration for Native project
    def result
    if(job.name() == "matrix-project"){
      // Define Label (ie host to run)
      result = job.axes 
    }else{
      log.debug( "This job has no matrix configuration : type = "+job.name())
    }
    return result
  }
  
  private def setMatrixConfiguration(def job, def axis){
    // Special configuration for Native project
    if(job.name() == "matrix-project"){
      log.debug("Setting a new Matrix configuration for job")

      // Define Label (ie host to run)
      def outputBuilder = new StreamingMarkupBuilder()
      //      String result1 = outputBuilder.bind{ mkp.yield job }

      if(axis instanceof Closure){
        job.axes << axis
      }else{
        job.axes.replaceNode { mkp.yield axis }
      }
      
      
      //      String result2 = outputBuilder.bind{ mkp.yield job }

    }else{
      log.warn( "This job is not a matrix project -> Can't set a new matrix configuration : type = "+job.name())
    }
  }
  
  
  boolean support(def job){
    return ["hudson.scm.CVSSCM", "hudson.scm.SubversionSCM"].isCase (job.scm.@"class" )
  }
  
  def cpplabels = ["Windows":"windows", "Linux":"rhel4", "any":"rhel4"]

  def createReportJobFromView(def view, def templatename, def forceDelete=false){
    def suffix = "_REPORT"
    this.copyView(view, templatename, suffix, forceDelete){ job, jobname ->
      if(job.name() == "matrix-project"){
        def mvn = this.from(job)
        def preferredLabel = cpplabels[mvn.os]
        if(job.combinationFilter.size()){
          // Replace
          job.combinationFilter = """(label=="${preferredLabel}")"""
        }else{
          // Add new one
          job <<  {
            combinationFilter("""(label=="${preferredLabel}")""")
          }
        }
      }
      return true
    }
  }
  
  def createReleaseJobFromView(def view, def templatename, def forceDelete=false){
    def suffix = "_RELEASE"
    this.copyView(view, templatename, suffix, forceDelete){ job, jobname ->
      def result = false
      def scm = this.getScmConfiguration(job)
      new MavenProject(scm) // Set the project name useful to find out the last release tag based on the ArtifactId 
      def tag = scm.getLastReleaseTag("pom.xml")
      def oldtag = scm.branch
      
      if(tag && oldtag != tag){
        scm.switchBranch(tag, true)
        this.setScmConfiguration(job, scm)
        result=true
        log.info("Update ${jobname} from tag $oldtag to the tag $tag")
      }else{
        log.debug("${jobname} is up-to-date")
      }
      
      return result
    }
    
    // 
  }
  
  /**
   * Copy jobs of a given view to another view but following a template one
   * 
   * @param original the original view
   * @param target the copy view
   * @param template the template job
   * @param suffix suffix to add tothe new job from the original job's name
   * @param forceDelete if true, replace the current job of the target view otherwise update it
   */
  def copyView(def original, def template, def suffix, def forceDelete=false, def closure=null){
    
    def originalJobs = this.getJobsView("${original}")

    log.debug("This process will copy all jobs from ${original}")

    // Original code from Groovy System script
    // Create or recreate the view is impossible for Jenkins REST #JENKINS-8927
    //def newView = createView("${newViewName}", update)
    def copyJobs=[]
    originalJobs.each() { 
      def jobname = it.name.text()
      def targetJobname = jobname+suffix
        
      this.copyJob( jobname, targetJobname, template, forceDelete, closure )
      copyJobs += targetJobname
    }
            
  }
  
  def copyJob(String original, String target, String template, boolean force=false, def closure=null){
    
    log.debug("Process job ${original}")
    if(this.existJob(original)){
      def copyjob
      def job = this.getJob(original)

      if(this.support(job)){
        def originalScm = this.getScmConfiguration(job)
          
        // Get it, Create it or Recreate it
        if(this.existJob(target)){
          if(force){
            // Force Recreation
            this.deleteJob(target)
            copyjob = this.createItem(target, template)
            // Transfer configuration from the original job
            this.setScmConfiguration(copyjob, originalScm)
            
          }else{
            // Get the old job and ...
            copyjob = this.getJob(target)
            // ... only update scm if the place of the original has been changed
            if( ! this.getScmConfiguration(copyjob).samePlace(originalScm) ){
              this.setScmConfiguration(copyjob, originalScm)
            }            
          }
        }else{
          // Create a new Job
          copyjob = this.createItem(target, template)
          // Transfer configuration from the original job
          this.setScmConfiguration(copyjob, originalScm)
        
        }       
        
        // Change Matrix Configuration (anyway ?)
        this.setMatrixConfiguration(copyjob, this.getMatrixConfiguration(job))
        this.updateJob(target, copyjob)
        
        // 2nd pass : specific to the process
        copyjob = this.getJob(target)
        if(closure?.call(copyjob, target)){
          this.updateJob(target, copyjob)

          // 3rd pass : enable the job
          if(this.isJobEnabled(original)){
            this.enableJob(target)
          }
        }
        
      }else{
        log.warn("Unsupported job configuration")
      }
        
    }else{
      log.error("Unexpected error : the job should be created ")
    }
          
      
  }

  def changeSCMTrigger(def item, def config){}
  
  def updateJobDependencies(def updatingView, def depViews=null){
    
    // Update dependencies between project from maven's dependencies
    log.debug( "--> Begin to update job trigger of release job from its dependencies described in maven")
    def jobsToUpdate = [:]
    
    def view = this.buildLinkBetweenHudsonAndMavenProjectName(updatingView)
    depViews?.each(){
      def map = this.buildLinkBetweenHudsonAndMavenProjectName(it)
     
      //view[it]=map
      view.mvn += map.mvn
    }
    //1rst pass : Clear all job dependencies
    //    this.getJobsView(updatingView).each{ item ->
    //      def job = this.getJob(item.name)
    //      this.clearJobBuildTrigger(job)
    //    }    
    view.jobs.each(){ jobname, value ->
      this.clearJobBuildTrigger(value.job)
    }

    // 2nd pass : Update each job
    view.jobs.each(){ jobname, value ->
      def pom = value.mvn
            
      if(pom.dependencies != null && pom.dependencies.size() > 0){
        log.debug("Update upstream job of ${jobname} with depencencies ${pom.dependencies}")
        for( dep in pom.dependencies){
          // update job trigger help to the artifact map
          def sai = MavenProject.toSimpleArtifactId(dep[1])
          
          
          
          if (view.mvn[sai]){
            updateJobBuildTrigger( view.mvn[sai].job, jobname)
            jobsToUpdate [view.mvn[sai].jobname] =  view.mvn[sai].job
          }else{
            log.warn("no hudson job corresponding to ${sai} (dependency of ${jobname})")
          }
          
        }
      }
    } 
    
    // still needed ???
    //    hudson.rebuildDependencyGraph();
    //    Logger.debug( "--> Dependency graph of Hudson rebuilt")
    
    // 3rd pass : update jobs
    jobsToUpdate.each { jobname, job->
      this.updateJob(jobname, job)
    }
    


  }
  
 
  def updateJobBuildTrigger(def job, def child){

    def bt = job.publishers."hudson.tasks.BuildTrigger"
    
    if(bt.size()==0){
      def trigger = this.toXmlNode(){
        "hudson.tasks.BuildTrigger"{
          childProjects()
          threshold{
            name("SUCCESS")
            ordinal("0")
            color("BLUE")
          }
        }
      }
      
      job.publishers.appendNode(trigger[0]) 
      
    }
    
    def triggeredJob= bt.childProjects.text() ?: ""
    // Recreate the new value of BuildTrigger
    if(!triggeredJob.contains(child)){
      
      // if trigger is not empty, add a comma to separate children projects
      triggeredJob = (triggeredJob ? "$triggeredJob, ": "")+child
      bt.childProjects = triggeredJob

      log.debug("new triggeredJob = $triggeredJob")
    }else{
      log.debug("triggeredJob already contains $child")
    }
    //        println "[DEBUG] ---> now ${upstreamjob.name} will trigger these jobs : "+upstreamjob.publishersList.get(hudson.tasks.BuildTrigger.class).childProjectsValue

    
  }
  
  def clearJobBuildTrigger(def job){
    
    job.publishers."hudson.tasks.BuildTrigger".childProjects.value=""
    
  }
  
  def rebuildAllJobOfView(def targetView){}
  
  def printProjectInfo(def referenceViews){
    def result = []
    def views = referenceViews.split(", ")
    if(views){
      views.each{ viewname ->
        this.getJobsView(viewname).each{ job ->
          
          def mvn = this.getMavenProject(this.getJob(job.name.text()))
          if(mvn){
            result+=[mvn.groupId, mvn.artifactId, mvn.version, mvn.scm, mvn.developers, mvn.os]
          }
          
        }
      }
    }else{
      log.error("Don't forget to set views (views)")

    }
    return result

  }
  
  
  def printlnImpact(def jobname, def pattern){
    def item = this.getItem(jobname)
    if(item){
      log.info("Project Run Line")
      
      item.downstreamProject.each(){ project ->
        log.debug("Root Job $jobname : analyze ${project.name.text()}")
        recursiveImpact( project.name.text(), pattern)
      }
    }

  }
 
  def recursiveImpact(def jobname, def pattern){
    log.debug("Analyze job $jobname with pattern $pattern")
    if(jobname){
      
      this.showLastLog(jobname, pattern)
      
      def item = this.getItem(jobname)
      item.downstreamProject.each(){ project ->
        log.debug("Node Job $jobname : recursive analyze ${project.name.text()} (DogWatch:$dogWatch)")
        dogWatch--
        if(dogWatch){
          recursiveImpact( project.name.text(), pattern)
        }else{
          log.error("Recursive Impact analysis reach the max depth allowed")
        }
        dogWatch++
      }
    }
  }

  def showLastLog(def jobname, def pattern){
    log.debug("Analyze Last Log of job $jobname with pattern $pattern")
    def item = this.getItem(jobname)
    def job = this.getJob(jobname)
    
    def buildnumber = item.lastBuild.number.text()
    if(buildnumber){
      log.debug("Analyze last build $buildnumber of job $jobname")
      // Get info from the last build
      def lastbuild = this.getBuild(jobname, buildnumber)
		if( lastbuild.result.text() != "SUCCESS" ){

		  // Retrieve developer from Maven Project
		  def developer =""
		  def maven = this.from(job)
		  if(maven){
			maven.project.developers?.developer?.each{
			  developer += "${it.id.text()} "
			}
		  }else{
			log.warn("Can't retrieve maven project from $job.name")
		  }
		  
		  // Find the console logs
		  if(job.name() == "matrix-project"){
			// Check every build ...
			lastbuild.run.each{run->
			  log.debug("run:$run")
			  // ... corresponding to the build
			  if(run.number.text() == buildnumber){
				// Download the output of the console 
				def runURL = "${run.url.text()}/consoleText"
				runURL.toURL().text.eachLine{line->
				  if(line ==~ pattern){
					// Parse line, find the pattern and log it
					log.info("$runURL\t$developer\t$line")
				  }
				}
			  }
			}

		  }else{
			// Download the output of the console 
			def runURL = "${lastbuild.url.text()}/consoleText"
			runURL.toURL().text.eachLine{line->
			  if(line ==~ pattern){
				// Parse line, find the pattern and log it
				log.info("$runURL\t$developer\t$line")
			  }
			}
		  }
		}else{
			  log.debug("Last build was successful")
		}
    }else{
      log.debug("No Build Number for job $jobname")
    }
  }
}