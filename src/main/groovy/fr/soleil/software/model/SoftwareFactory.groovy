package fr.soleil.software.model

import fr.soleil.software.jenkins.model.JenkinsREST
import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.maven.model.MavenProject
import fr.soleil.software.maven.model.MavenBOM
import fr.soleil.software.maven.model.MavenPackaging
import fr.soleil.software.sonar.model.SonarServer
import fr.soleil.software.ica.IcaConfiguration
import fr.soleil.software.ica.IcaProxy
import fr.soleil.software.model.SoftwareDirectory


import com.google.common.io.Files
import org.slf4j.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper


enum DeleteType { KEEP, CLOSE, MOVE }

class SoftwareFactory{
  
  Logger log = LoggerFactory.getLogger(SoftwareFactory.class)
  
  // ICA Configuration
  def ica= IcaConfiguration.datum
  
  JenkinsREST jenkins 
  SonarServer sonar = new SonarServer()
  
  def ids
  
  def bomRoot = ":ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS"
  def bomPath = "ContinuousIntegration/maven/bom/"
  
  def packagingRoot = ":ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS"
  def packagingPath = "ContinuousIntegration/maven/packaging/"
  
  static def EXCLUDED_FILES = [".project",".classpath",".settings","target"]

  /**
   * Retrieve all ID
   * The key is groupId.simpleArtifactId
   * Maven is : groupId.artifactId
   * Jenkins is : $jobname
   * SCM is : root + module
   * Sonar is : groupId:artifactId (here artifactId is specific to a platform ; ie fr.soleil.lib:YAT-i386-Linux-gcc-static-release)
   */
  SoftwareFactory(){
    
    IcaProxy.configureProxy()
    jenkins = new JenkinsREST("http://calypso/jenkins/".toURL())
    ids = new SoftwareDirectory(jenkins, sonar)
    
    SCM.forceRealms=true

  }
  
  static final void main(String[] args){

    def sf = new SoftwareFactory()
    sf.ids.softwares.each(){ key, value ->
    println "key:$key   value:$value"
    }
    println "Number of software : ${sf.ids.softwares.size()}"
  }
  
  /**
   * To abstract
   * Return the project which corresponding to key and value
   */
  def getProjectFrom(def what, def value){
    
    def key = ids.software.keySet().find(){ ids[it]."$what" == value }
    log.debug("key=$key <= value=$value of what = $what")
    
    return ids[key].project
  }
  
  
  
  def addProject(def groupId, def simpleArtifactId, def packaging, def platform, def scmroot, def scmmodule, def developer,File files=null, def message){
    
    
    def scm = ScmBuilder.create(scmroot, scmmodule)
    def pomxml
    // Create File project
    File tmp 
    def dirToImport
    if(!files){
      tmp = Files.createTempDir()
      tmp.deleteOnExit()
      pomxml = new File(tmp,"pom.xml")
      pomxml.text = IcaConfiguration.createNewPOM(groupId, simpleArtifactId, packaging, platform, scm, developer)
    }else{
      log.debug("got files from $files.absolutePath")
      tmp = files
      tmp.eachFileMatch(EXCLUDED_FILES) { it.file ? it.delete() : it.deleteDir() }
      pomxml = new File(tmp,"pom.xml")
      log.debug("files filtered with $EXCLUDED_FILES")
    }
    
    if(pomxml.exists() ){
      // Import the new module
      MavenProject mvn = this.addToSCM(scm, tmp, message)
      this.importProject(mvn)

    }else{
      log.error("==**OPERATION ABORTED**== The given files to import has no pom.xml to its root")
    }

  }
  
  def importProjectByScm(def scmRoot, def scmPath){
    log.debug( "Project from Scm to release => ${scmRoot} ${scmPath}")
    def scm = ScmBuilder.create(scmRoot, scmPath)
    this.importProject(new MavenProject(scm))
  }

  def importProject(MavenProject mvn){
    
    def software = this.ids.add(("$mvn.groupId.$mvn.simpleArtifactId"), mvn.scm.devURL )
    
    def data = IcaConfiguration.getDataFor(mvn)
    if(data){
      // Jenkins Job
      log.info("STEP 1 : create Jenkins job (from template ${data.jenkins})")
      jenkins.createMavenJob(software.jobKey, mvn, data.jenkins)

      log.info("STEP 1 : OK for $software.jobKey")

      // Maven BOM
      // In 3rd, if build success, update corresponding BOM
      log.info("STEP 3 : Update BOM : "+data.bom)
      def bom = IcaConfiguration.getMavenBom(data.bom).updateDependency(mvn)
      log.info("STEP 3 : OK")
      
      def i=0;
      data.distribution?.each{ packaging ->
        log.info("[OPTION] STEP 4 : Update packaging project "+packaging)
        
        if(IcaConfiguration.getMavenPackaging(packaging).addDependency(mvn)){
          log.info("[OPTION] STEP 4.${i++} : OK")
        }else{
          log.info("[OPTION] STEP 4.${i++} : FAILED")
        }
      }

      
    }else{
      log.error("This project is not compatible : $mvn")
    }
  }

  /**
   * Move the given project identified by the groupId and artifactId
   * to the new SCM repository
   * - Scm (MOVE or OBSOLETE case)
   */
  def moveProject(def groupId, def artifactId, def scmroot, def scmmodule, String message){
    
    String key = "${groupId}.${MavenProject.toSimpleArtifactId(artifactId)}"
    
    def mvn = this.ids[key].project    
    // No impact in SONAR
    // TODO change scm in maven
    // mvn.setSCM
    // 
    // move source in the new SCM
    def oldscm = mvn.scm 
    def newscm = ScmBuilder.create(scmroot, scmmodule)

    File tmp = Files.createTempDir()
    tmp.deleteOnExit()  
    def sources = oldscm.exportFile(tmp) as File


    // A Smooth way is to change the Scm in jenkins job
    // Move SCM
    mvn = this.addToSCM(newscm, sources, message)
    
    // Update Jenkins jobs with the new scm
    // NB : Just the main job ; the other will be updated automatically
    jenkins.setScmConfiguration(ids[key].job, newscm)
    jenkins.updateJob(ids[key].jobKey, ids[key].job)
    
    // Just close without clear the scm part of the id
    oldscm.close("MOVE_TO", "Move to $newscm.userURL")
    
    // Recreate the software id in the directory
    ids.add(key, ids[key].jobKey, ids[key].qualityKey, newscm.devURL)

  }

  /**
   * Delete the given project identified by the groupId and artifactId
   * from :
   * - Jenkins (HEAD, RELEASE, REPORT)
   * - Sonar 
   * - Maven (BOM, PACKAGING)
   * - Scm (MOVE or OBSOLETE case)
   */
  def deleteProject(def groupId, def artifactId, DeleteType type, String message){
    
    String key = "${groupId}.${MavenProject.toSimpleArtifactId(artifactId)}"
    log.debug( "key to delete $key => ${ids[key]}")
    
    if(ids[key]){
      log.info( "Begin to delete ${ids[key]}")
      deleteFromSonar(ids[key])
      deleteFromMaven(ids[key])
      deleteFromSCM(ids[key], DeleteType.CLOSE, message)

      // Last thing to delete : Jenkins is the reference
      deleteFromJenkins(ids[key])

      // Remove the key
      ids.softwares.remove(key)
      log.info("Deleted $groupId $artifactId $type $message")
    }else{
      log.warn("Key \"$key\" not found -> No deletion")
    }
  }
  
  protected def deleteFromJenkins(def id){
    if(id.job){
      log.info("Deleting from Jenkins : $id")    
      jenkins.deleteJob(id.job)
      jenkins.deleteJob("${id.job}_RELEASE")
      jenkins.deleteJob("${id.job}_RELEASE_REPORT")
      jenkins.deleteJob("${id.job}_REPORT")
    
      log.info("Deleted in Jenkins : $id")    
    }else{
      log.warn("No information about this Jenkins project -> No delete ($id)")    
    }
  }
  
  protected def addToJenkins(def id, MavenProject mvn, def templateJob){
    jenkins.createMavenJob(id.jobKey, mvn, templateJob)
  }

  
  protected def deleteFromSonar(def id){
    if( id.qualityKey && this.sonar.isValidKey( id.qualityKey ) ){
      log.info("Deleting from Sonar : $id")    
      sonar.deleteProject(id.qualityKey)

      log.info("Deleted in Sonar : $id")    
    }else{
      log.warn("No information about this Sonar project -> No delete ($id)")    
    }
  }
  
  protected def deleteFromMaven(def id){
    if(id.project){
      // Delete from Maven BOM
      log.info("Deleting from Maven : $id")    

      def bom = IcaConfiguration.getDataFor(id.project).bom
      if( bom ){
        new MavenBOM(ScmBuilder.create(bomRoot,bomPath+bom)).removeDependency(id.project)
      }
    
      // Delete from Maven Packaging
      def distributions = IcaConfiguration.getDataFor(id.project).distribution
      distributions.each(){ distribution ->
        new MavenPackaging(ScmBuilder.create(packagingRoot,packagingPath+distribution)).removeDependency(id.project)
      }
    
      log.info("Deleted in Maven : $id")    
      
    }else{
      log.warn("No information about this Maven project -> No delete ($id)")    
    }
  }
  
  def deleteFromSCM(def id, def type, def message){
    if(id.scm){
      // Delete from SCM, add OBSOLETE readme file and set read only the path
      log.info("Deleting from SCM : $id")    
      

      switch(type){

      case DeleteType.CLOSE :
        id.scm.close("OBSOLETE", message)

        break;

      case DeleteType.MOVE :
        id.scm.close("MOVE_TO", message)
        break;

      default:
        break;
      }

      log.info("Deleted in SCM : $id")    
      
    }else{
      log.warn("No information about this SCM project -> No delete ($id)")    
    }
  }
  
  def addToSCM(SCM scm, File file, def message = "SoftwareFactory-addToSCM"){
    
    scm.importFile(file, "", message)
    
    EXCLUDED_FILES.each(){
      scm.addToIgnore(it)
    }
    
    // 2. Create all stuff surround the project
    MavenProject newProject = new MavenProject(scm)
    // Force the Tag if necessary
    if(!newProject.isSnapshot()){
      newProject.release(true)
    }
    
    return newProject
  }
  
  /**
   * Release based on scm information (not based on index)
   * 
   */
  def releaseByJob(def jobname){
    log.debug( "Project from Jenkins Job to release => ${jobname}")
    def software = ids.findFromJobKey(jobname)
    if(software?.project){
      def mvn = software.project
      if(mvn.isSnapshot()){
        mvn.release()
      }else{
        log.warn("Nothing to do -> $mvn.simpleArtifactId is already a release ($mvn.version) ")
      }      
    }else{
      
      log.error("Nothing to do -> $mvn.simpleArtifactId is already a release ($mvn.version) ")
    }
  }
  
  /**
   * Release based on scm information (not based on index)
   * 
   */
  def releaseByScm(def scmRoot, def scmPath){
    // 2. Create all stuff surround the project
    log.debug( "Project from Scm to release => ${scmRoot} ${scmPath}")
    def scm = ScmBuilder.create(scmRoot, scmPath)
    MavenProject mvn = new MavenProject(scm)
    if(mvn.isSnapshot()){
      mvn.release()
    }else{
      log.warn("Nothing to do -> $mvn.simpleArtifactId is already a release ($mvn.version) ")
    }
    
  }
  
  /**
   * Release based on software factory's key
   * 
   */
  def release(def key){
    log.debug( "key to release $key => ${ids[key]}")
    def id = ids[key]
    if(id?.project){
      log.info( "Begin to release $id")
      if(!id.project.isSnapshot()){
        id.project.release(true)
      }
      
    }else{
      log.warn("Key \"$key\" not found -> No release")
    }
  }
  
}
