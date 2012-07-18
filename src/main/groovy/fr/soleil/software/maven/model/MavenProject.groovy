package fr.soleil.software.maven.model

import com.google.common.io.Files

import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.scm.model.SubversionJava
import fr.soleil.software.util.XML

import org.slf4j.*

/**
 * Represents a Maven project
 * 
 * @TODO Replace with model from API
 */
class MavenProject {
  
  Logger log = LoggerFactory.getLogger(MavenProject.class)
    
  def pom
  def project
  def scm
  
  // From Maven low-level API, no warranty about upstream compatibility
  static def modelReader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader()
  static def modelWriter = new org.apache.maven.model.io.xpp3.MavenXpp3Writer()
  
  static def toSimpleArtifactId(def artifactId){
    def index = artifactId.indexOf("-")
    if(index>0){
      artifactId = artifactId[0..index-1]
    }
    return artifactId
  }
  
  public MavenProject(SCM scm){

    this.scm = scm
    updatePOM()
  }
  
  private void updatePOM(){
    pom = this.scm.checkout("pom.xml", scm.tag)

    try{
      project = new XmlParser().parseText("""${pom}""")
      this.scm.project= this.simpleArtifactId // Usefull to retrieve release tag
    }catch(Exception e){
      throw new Exception("Unable to parse text for ${scm.root} ${scm.branch} ${scm.module}/pom.xml: "+ pom, e)
    }

  }

  String getVersion(){
    return project.version.text()
  }
    
  boolean isSnapshot(){
    return project.version.text().endsWith("-SNAPSHOT")
  }
  
  String getSimpleArtifactId(){
    return toSimpleArtifactId(this.artifactId)
  }
  
  String getArtifactId(){
    return project.artifactId.text()
  }
  
  String getGroupId(){
    return project.groupId.text()
  }
  
  def getDependencies(){
    def result = []
    if(project.dependencies != null && project.dependencies[0] != null){
      for (d in project.dependencies[0]){
        result.add([d.groupId.text(), d.artifactId.text()])
      }
    }
    return result
  }
  /**
   * Return all dependencies from any profile even the default
   */
  def getAllDependencies(){
    def result = []
    if(project.dependencies != null && project.dependencies[0] != null){
      for (d in project.dependencies[0]){
        result.add([d.groupId.text(), d.artifactId.text()])
      }
    }
    project.profiles?.getAt(0)?.each(){ profile ->
      profile.dependencies?.getAt(0)?.each(){ d ->
        result.add([d.groupId.text(), d.artifactId.text()])
      }
    }
    return result
  }

  String getOs(){
    def os = "any"
    if(project.build.plugins[0] != null){ // No plugin ?
      for(plugin in project.build.plugins[0]){
        if(plugin.groupId.text() == "org.freehep" && plugin.artifactId.text() == "freehep-nar-plugin"){
          os = plugin.configuration.os.text()
          os = os == "" ? "any" : os
        }
      }
    }
    return os
  }

  /**
   * Retrieve the packaging of the project
   * 
   * @return the packaging if not null or empty else "jar"
   */
  String getPackaging(){
    return project.packaging?.text() ?:"jar"
  }
  
  def getDevelopers(){
    def result = []
    if(project.developers != null && project.developers[0] != null){
      for (d in project.developers[0]){
        result.add(d.id.text())
      }
    }
    return result
    
  }

  
  def prepareSnapshot(){
    def model // THE TRUE MODEL from Maven API
    def pomFile = scm.checkout("pom.xml","","Migration") as File // pom.xml file
    // Open it and read it from Maven API
    pomFile.withReader{ reader ->
      model = modelReader.read( reader)
      // close automatically at scope end
    }

    if(model){
      def newversion = ""
      if(!model.version.endsWith("-SNAPSHOT")){
        model.version = this.upgradeMinorVersion()+"-SNAPSHOT"
        log.info("Project ${this.project.artifactId.text()} is a RELEASE -> prepare new snapshot ${model.version}")
        // write xml file
        pomFile.withWriter{ writer ->
          modelWriter.write(writer,model)
        }
        // commit file
        def description = "prepare next snapshot"
        log.debug("""Launch mvn.scm.commit($pomFile.path,$description)""")
        scm.commit(pomFile.path, "$description")
        
        updatePOM()
      }else{
        log.info("Project ${this.project.artifactId.text()} is already a SNAPSHOT")
      }
    }else{
      log.error("Can't load the model ")
    }
  }

  /**
   * Upgrade the minor version X.Y.Z --> X.Y.(Z+1)
   * Becarefull this method doesn't check SNAPSHOT i.e X.Y.Z-SNAPSHOT --> X.Y.(Z+1)
   * Becarefull this method modifies the current object without synchronize with filesystem or scm system
   */
  def upgradeMinorVersion(){
    def newVersion = this.version.replaceAll(/(\d+)\.(\d+)\.(\d+)/){ all, x,y,z ->
      "$x.$y.${z.toInteger()+1}"
    }
    pom = XML.replace(pom,/(.*)(<version>).*(\d+)\.(\d+)\.(\d+).*(<\/version>)(.*)/,1){ all, before, stag,x,y,z,etag, after ->
      log.debug ("Found version to upgrade")
      newVersion="$x.$y.${z.toInteger()+1}"
      return "$before$stag$newVersion$etag$after"
    }
    try{
      project = new XmlParser().parseText("""${pom}""")
      this.scm.project= this.simpleArtifactId // Usefull to retrieve release tag
    }catch(Exception e){
      throw new Exception("Unable to parse text for ${scm.root} ${scm.branch} ${scm.module}/pom.xml: "+ pom, e)
    }

    return newVersion
  }
  
  
  /**
   * change some thing to the project
   * Manage the version in SCM repository
   */
  boolean change(Closure c){
    def result=false

    // Don't migrate if SVN + Tag
    if(!(scm.tag && scm instanceof SubversionJava) ){
      
      //def modelReader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader()
      //def modelWriter = new org.apache.maven.model.io.xpp3.MavenXpp3Writer()

      def model // THE TRUE MODEL from Maven API
      def pomFile = scm.checkout("pom.xml","","Migration") as File // pom.xml file
      // Open it and read it from Maven API
      pomFile.withReader{ reader ->
        model = modelReader.read( reader)
        // close automatically at scope end
      }

      if(model){
        def description = c.call(model)
        if(description){

          def newversion = ""
          if(!this.snapshot){
            newversion = this.upgradeMinorVersion() // TODO Optimize
            model.version = newversion
            log.info("Project ${this.project.artifactId.text()} is a RELEASE -> Upgrade and Tag with new version $newversion (oldversion is ${this.version}")
          }else{
            log.info("Project ${this.project.artifactId.text()} is a SNAPSHOT -> No Tag")
          }
          // write xml file
          pomFile.withWriter{ writer ->
            modelWriter.write(writer,model)
          }
          // commit file
          log.debug("""Launch mvn.scm.commit($pomFile.path,$description)""")
          scm.commit(pomFile.path, "$description")
          
          this.updatePOM()
          // Tag all project files with new version
          if(!this.snapshot){
            // release the head
            scm.release(newversion,"",this.&prepareSnapshot)
            log.debug("""Release it""")
          }
        
          result=true
          
        }else{
          log.debug("No Dependency found")
        }
      }else{
        log.error("Something goes wrong with the packaging project : $scm.module")
      }

    }else{
      log.info("No support : The current url is a tag ($scm)")
    }

    return result

  }
  
  def release(boolean force=false){
    if(this.snapshot || force){
      File tmp = Files.createTempDir()
      tmp.deleteOnExit()
    
      def model // THE TRUE MODEL from Maven API
      def pomFile = scm.checkout("pom.xml", "", tmp.absolutePath ) as File // pom.xml file
      // Open it and read it from Maven API
      pomFile.withReader{ reader ->
        model = modelReader.read( reader)
        // close automatically at scope end
      }

      if(model){
        def description = "prepare release"

        // 1. remove SNAPSHOT from version
        model.version -= "-SNAPSHOT"
        log.info("Project ${this.project.artifactId.text()} is a RELEASE -> Upgrade and Tag with new version $model.version")
        // write xml file
        pomFile.withWriter{ writer ->
          modelWriter.write(writer,model)
        }
        // 2. commit pom
        log.debug("""Launch mvn.scm.commit($pomFile.path,$description)""")
        scm.commit(pomFile.path, "$description")
        this.updatePOM()
        
        // 3. release 
        log.debug("""Release it""")
        scm.release(model.version,"",this.&prepareSnapshot)
      }
    }else{
      log.warn("Already a release version")
    }
  }
  
  boolean replaceDependency(def groupId1, def artifactId1, def groupId2, def artifactId2, def description=""){
    
    def result=false

    // Don't migrate if SVN + Tag
    if(!(scm.tag && scm instanceof SubversionJava) ){
      
      //def modelReader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader()
      //def modelWriter = new org.apache.maven.model.io.xpp3.MavenXpp3Writer()

      def model // THE TRUE MODEL from Maven API
      def pomFile = scm.checkout("pom.xml","","Migration") as File // pom.xml file
      // Open it and read it from Maven API
      pomFile.withReader{ reader ->
        model = modelReader.read( reader)
        // close automatically at scope end
      }

      if(model){
        //def dep = Class.forName("org.apache.maven.model.Dependency", true, hudson.model.Hudson.instance.getPluginManager().uberClassLoader).newInstance() // Force class loading
        // check dependency
        def replaced=false 
        for (dependency in model.dependencies){
          if(dependency.groupId == groupId1 && dependency.artifactId == artifactId1){
            dependency.groupId = groupId2
            dependency.artifactId = artifactId2
            replaced = true
            log.debug("New dependency : ${dependency.groupId} ${dependency.artifactId}")
            break
          }
        }
        
        if(replaced){

          def newversion = ""
          if(!this.snapshot){
            newversion = this.upgradeMinorVersion() // TODO Optimize
            model.version = newversion
            log.info("Project ${this.project.artifactId.text()} is a RELEASE -> Upgrade and Tag with new version $newversion (oldversion is ${this.version}")
          }else{
            log.info("Project ${this.project.artifactId.text()} is a SNAPSHOT -> No Tag")
          }
          // write xml file
          pomFile.withWriter{ writer ->
            modelWriter.write(writer,model)
          }
          // commit file
          log.debug("""Launch mvn.scm.commit($pomFile.path,$description)""")
          scm.commit(pomFile.path, "$description : Replace $groupId1.$artifactId1 by $groupId2.$artifactId2")

          // Tag all project files with new version
          if(!this.snapshot){
            // release the head
            scm.release(newversion,"",this.&prepareSnapshot)
            log.debug("""Release it""")
          }
        
          result=true
          
        }else{
          log.debug("No Dependency found")
        }
      }else{
        log.error("Something goes wrong with the packaging project : $scm.module")
      }

    }else{
      log.info("No support : The current url is a tag ($scm)")
    }

    return result
  }
  
  def getAllModules(){
    def result=[]
    
  }

}
